package com.archseoul.kafkastudy.balanceworker.consume

import com.archseoul.kafkastudy.balanceworker.domain.ApplyResult
import com.archseoul.kafkastudy.balanceworker.domain.BalanceService
import com.archseoul.kafkastudy.common.LedgerEvent
import org.slf4j.LoggerFactory
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

/**
 * `ledger-events` 를 `balance-group` 으로 구독해 잔액을 계산한다.
 *
 * ## 인스턴스가 여러 개가 된다
 *
 * 이 워커는 4단계에서 **여러 개를 띄운다.** 같은 컨슈머 그룹이므로 파티션이 나뉘어 배정되고,
 * **한 파티션은 한 워커만** 처리한다.
 *
 * 파티션 키가 `userId` 이므로 **같은 사용자의 거래는 항상 한 워커가 순서대로** 처리한다.
 * → 순서 보장과 동시성 안전이 **동시에** 얻어진다.
 *
 * (그래서 여기서는 잔액 갱신에 별도의 락이나 원자적 upsert 가 필요 없다.
 *  4단계에서 키를 `RANDOM` 으로 바꾸면 이 전제가 깨지고 잔액이 틀어진다.)
 *
 * ## 멱등 처리는 아직 없다
 *
 * Kafka 의 전달 보장은 **at-least-once** 이므로 같은 이벤트를 두 번 받을 수 있다.
 * 방어는 **5단계에서** 추가한다 — 재생 시 포인트가 두 배가 되는 문제를 먼저 겪어야 한다.
 */
@Component
class LedgerConsumer(
    private val balanceService: BalanceService,
    private val throttle: ReplayThrottle,
    private val replay: ReplayCoordinator,
) : ConsumerSeekAware {

    private val log = LoggerFactory.getLogger(javaClass)

    // ⚠️ ConsumerSeekAware 는 **@KafkaListener 가 붙은 리스너 빈**이 구현해야 콜백이 불린다.
    //    별도 빈으로 두면 Spring Kafka 가 찾지 못해 호출되지 않는다.
    //    그래서 리스너가 콜백을 받아 ReplayCoordinator 에 넘긴다.

    override fun registerSeekCallback(cb: ConsumerSeekAware.ConsumerSeekCallback) {
        replay.registerCallback(cb)
    }

    override fun onPartitionsAssigned(
        assignments: Map<TopicPartition, Long>,
        cb: ConsumerSeekAware.ConsumerSeekCallback,
    ) {
        // 여기서 seekToBeginning 을 부르지 않는다.
        // 기동 시 자동 재생은 실무 패턴이 아니다 — 커밋된 offset 에서 이어 읽는 것이 정상이다.
        // cb 는 넘기지 않는다 — 즉시 seek 방식이라 HTTP 스레드에서 쓰면 컨슈머가 깨진다.
        replay.onAssigned(assignments.keys)
    }

    @KafkaListener(
        topics = ["\${app.topic.ledger-events}"],
        groupId = "\${app.consumer-group.balance}",
    )
    fun consume(
        event: LedgerEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        throttle.pause()

        when (val result = balanceService.apply(event)) {
            is ApplyResult.Applied -> log.info(
                "적용 p{}#{} {} {} {} → {}",
                partition, offset, event.userId, event.eventType,
                result.before, result.after,
            )

            is ApplyResult.Rejected -> log.info(
                "거절 p{}#{} {} 잔액={} 요청={}",
                partition, offset, event.userId, result.balance, result.requested,
            )

            is ApplyResult.Skipped -> log.info(
                "건너뜀 p{}#{} {} eventId={} (이미 처리함)",
                partition, offset, event.userId, result.eventId,
            )
        }
    }
}
