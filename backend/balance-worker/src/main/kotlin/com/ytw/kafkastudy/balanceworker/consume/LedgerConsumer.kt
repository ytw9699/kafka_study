package com.ytw.kafkastudy.balanceworker.consume

import com.ytw.kafkastudy.balanceworker.domain.ApplyResult
import com.ytw.kafkastudy.balanceworker.domain.BalanceService
import com.ytw.kafkastudy.common.LedgerEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${app.topic.ledger-events}"],
        groupId = "\${app.consumer-group.balance}",
    )
    fun consume(
        event: LedgerEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
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
        }
    }
}
