package com.archseoul.kafkastudy.balanceworker.consume

import com.archseoul.kafkastudy.balanceworker.domain.BalanceService
import java.util.concurrent.atomic.AtomicReference
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.stereotype.Component

/**
 * offset 을 처음으로 되감아 전체를 다시 읽게 한다.
 *
 * ## 왜 필요한가
 *
 * [KS-18](../../../../../../../../docs/issues/KS-18-worker-restart-offset.md)에서 확인했듯
 * **재기동은 이어읽기다.** `auto-offset-reset: earliest` 는 커밋된 offset 이 없을 때만 동작한다.
 *
 * 전체 재생을 하려면 **명시적으로 seek** 해야 한다.
 *
 * ## `KafkaConsumer` 는 thread-safe 하지 않다
 *
 * REST 요청은 HTTP 스레드에서 오고 컨슈머는 리스너 스레드에서 돈다.
 * REST 스레드에서 컨슈머를 직접 건드리면 안 된다.
 *
 * [ConsumerSeekAware.registerSeekCallback] 에서 받은 콜백을 보관해두고 그것을 호출하면,
 * **실제 seek 은 다음 poll 직전에 컨슈머 스레드에서 적용된다.**
 */
@Component
class ReplayCoordinator {

    private val log = LoggerFactory.getLogger(javaClass)

    private val callback = AtomicReference<ConsumerSeekAware.ConsumerSeekCallback?>()

    @Volatile
    private var assigned: Set<TopicPartition> = emptySet()

    /**
     * ⚠️ [ConsumerSeekAware] 는 **`@KafkaListener` 가 붙은 리스너 빈**이 구현해야 콜백이 불린다.
     * 별도 빈으로 두면 Spring Kafka 가 찾지 못한다.
     * 그래서 [LedgerConsumer] 가 콜백을 받아 여기로 넘겨준다.
     *
     * ⚠️⚠️ **두 콜백은 서로 다르다. 이것을 반드시 구분해야 한다.**
     *
     * | 출처 | 구현체 | 동작 |
     * |---|---|---|
     * | `registerSeekCallback` | `ListenerConsumer` | **지연 seek** — 요청을 큐에 넣고 다음 poll 직전에 적용 |
     * | `onPartitionsAssigned` | `InitialOrIdleSeekCallback` | **즉시 seek** — `consumer.seekToBeginning()` 직접 호출 |
     *
     * 후자를 다른 스레드(HTTP)에서 부르면
     * `ConcurrentModificationException: KafkaConsumer is not safe for multi-threaded access` 가 난다.
     *
     * **런타임 seek 에는 반드시 `registerSeekCallback` 쪽을 써야 한다.**
     */
    fun registerCallback(cb: ConsumerSeekAware.ConsumerSeekCallback) {
        callback.set(cb)
        log.info("지연 seek 콜백 등록됨")
    }

    /**
     * 배정 파티션만 갱신한다.
     *
     * **여기서 받은 콜백은 저장하지 않는다.** 즉시 seek 방식이라
     * HTTP 스레드에서 호출하면 컨슈머를 깨뜨린다.
     */
    fun onAssigned(partitions: Set<TopicPartition>) {
        assigned = partitions
        log.info("재생 대상 파티션 갱신: {}", partitions.map { it.partition() }.sorted())
    }

    /**
     * 담당 파티션을 처음으로 되감는다.
     *
     * ⚠️ **이 워커가 담당한 파티션만** 되감긴다.
     * 워커가 여러 개면 각자에게 호출해야 한다.
     * 5단계 실험은 워커 1개로 진행하는 이유가 이것이다 —
     * 재생의 본질(offset seek)을 보는 것이 목적이고 분산 조율은 다른 주제다.
     */
    fun seekToBeginning(): Set<TopicPartition> {
        val cb = callback.get() ?: error("아직 파티션을 배정받지 못했습니다")
        val targets = assigned
        require(targets.isNotEmpty()) { "배정된 파티션이 없습니다" }

        cb.seekToBeginning(targets)
        log.info("재생 요청 partitions={}", targets.map { it.partition() }.sorted())
        return targets
    }
}

/**
 * 재생 시 이벤트마다 잠깐 쉰다.
 *
 * 수십 건은 수백 ms 에 끝나 화면에서 과정이 보이지 않으므로, 데모 시 50ms 정도를 준다.
 * 프로덕션에는 없을 설정이다.
 */
@Component
class ReplayThrottle(
    @Value("\${app.replay.delay-ms:0}") private val delayMs: Long,
) {
    fun pause() {
        if (delayMs > 0) Thread.sleep(delayMs)
    }
}
