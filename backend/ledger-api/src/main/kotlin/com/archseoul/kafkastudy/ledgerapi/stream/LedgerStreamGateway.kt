package com.archseoul.kafkastudy.ledgerapi.stream

import com.archseoul.kafkastudy.common.LedgerEvent
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Header
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * `ledger-events` 전체를 구독해 브라우저에 SSE 로 중계한다.
 *
 * ## 왜 `balance-worker` 가 아니라 여기에 두는가
 *
 * `balance-worker` 는 4단계에서 **여러 인스턴스**로 늘어난다.
 * 거기에 SSE 를 붙이면 브라우저가 그중 **하나에만** 연결되어
 * 그 인스턴스가 배정받은 파티션의 이벤트만 보게 된다.
 *
 * `ledger-api` 는 단일 인스턴스이고 **별도 컨슈머 그룹**(`gateway-group`)으로 전체 파티션을
 * 구독하므로 이 문제가 없다.
 *
 * 우회가 아니라 레이어 분리다 — **작업 분배(컨슈머 그룹)와 브로드캐스트(SSE)는 다른 문제**다.
 * 0단계 CLI 실습에서 확인한 "다른 그룹이면 둘 다 받고, 같은 그룹이면 나눠 갖는다"가 여기서 설계로 쓰인다.
 */
@Component
class LedgerStreamGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 연결된 브라우저들.
     *
     * 순회 중에 다른 스레드가 제거할 수 있으므로 [CopyOnWriteArrayList] 를 쓴다.
     * (Kafka 리스너 스레드가 순회하고, HTTP 스레드가 추가/제거한다)
     */
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    fun subscribe(): SseEmitter {
        // 0 = 무제한. 프록시가 중간에 끊으면 브라우저가 자동 재연결한다.
        val emitter = SseEmitter(0L)

        // ⚠️ 끊긴 emitter 를 목록에서 빼지 않으면 계속 쌓여 메모리가 샌다.
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }

        emitters.add(emitter)
        log.info("SSE 연결됨. 현재 {}개", emitters.size)

        // 연결 직후 한 번 보내 프록시/브라우저가 스트림을 확정하게 한다.
        runCatching { emitter.send(SseEmitter.event().name("connected").data(mapOf("ok" to true))) }

        return emitter
    }

    /**
     * `gateway-group` 으로 구독한다.
     *
     * `auto-offset-reset: latest` — 실시간 피드이므로 기동 이후 들어온 것만 보면 된다.
     * (과거를 읽는 것은 `balance-worker` 와 `fraud-service` 의 몫이다)
     */
    @KafkaListener(
        topics = ["\${app.topic.ledger-events}"],
        groupId = "\${app.consumer-group.gateway}",
    )
    fun relay(
        event: LedgerEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        // partition/offset 을 함께 보내는 것이 학습상 핵심이다.
        // 화면에서 "같은 userId 가 같은 파티션에 들어가는지"를 뱃지로 확인한다.
        val payload = mapOf(
            "eventId" to event.eventId.toString(),
            "eventType" to event.eventType,
            "occurredAt" to event.occurredAt.toString(),
            "userId" to event.userId,
            "amount" to event.amount,
            "reason" to event.reason,
            "partition" to partition,
            "offset" to offset,
        )

        val dead = mutableListOf<SseEmitter>()
        emitters.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().name("ledger").data(payload))
            } catch (e: IOException) {
                // 브라우저가 이미 닫은 연결. 조용히 정리한다.
                dead += emitter
            } catch (e: IllegalStateException) {
                dead += emitter
            }
        }
        if (dead.isNotEmpty()) {
            emitters.removeAll(dead)
            log.info("끊긴 SSE {}개 정리. 남은 연결 {}개", dead.size, emitters.size)
        }
    }

    fun connectionCount(): Int = emitters.size
}
