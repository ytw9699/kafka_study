package com.ytw.kafkastudy.ledgerapi.publish

import com.ytw.kafkastudy.common.LedgerEvent
import com.ytw.kafkastudy.ledgerapi.config.PartitionKeyModeHolder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * 거래 이벤트를 `ledger-events` 토픽에 발행한다.
 *
 * **`ledger-api` 는 DB 를 갖지 않는다.** Kafka 가 원장의 원본(source of truth)이고,
 * 잔액은 `balance-worker` 가 이 로그로부터 계산해내는 파생 상태다.
 */
@Component
class LedgerEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, LedgerEvent>,
    private val keyMode: PartitionKeyModeHolder,
    @Value("\${app.topic.ledger-events}") private val topic: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 발행하고 브로커 확인(ack)을 기다린 뒤 적재 위치를 돌려준다.
     *
     * `KafkaTemplate.send()` 는 비동기(`CompletableFuture`)다.
     * 여기서 `.get()` 으로 기다리는 것은 **학습 목적** — 응답에 partition/offset 을 담아
     * "내 거래가 어느 파티션 몇 번에 들어갔는지"를 화면에서 바로 보기 위함이다.
     *
     * 실무에서는 이 대기가 그대로 **응답 지연**이 되므로 보통 콜백으로 처리한다.
     */
    fun publish(event: LedgerEvent): PublishResult {
        val mode = keyMode.get()
        val key = mode.keyFor(event.userId)

        val metadata = kafkaTemplate.send(topic, key, event).get().recordMetadata

        log.info(
            "발행 eventId={} type={} userId={} key={} → partition={} offset={}",
            event.eventId, event.eventType, event.userId, key,
            metadata.partition(), metadata.offset(),
        )

        return PublishResult(
            eventId = event.eventId.toString(),
            partition = metadata.partition(),
            offset = metadata.offset(),
            keyMode = mode.name,
            partitionKey = key,
        )
    }
}

data class PublishResult(
    val eventId: String,
    /** 이 이벤트가 들어간 파티션. 같은 `userId` 면 항상 같아야 한다 (`USER_ID` 모드일 때). */
    val partition: Int,
    val offset: Long,
    /** `USER_ID`(정상) 또는 `RANDOM`(실험 A). 화면에서 현재 모드를 확인하기 위함. */
    val keyMode: String,
    /** 실제로 사용된 파티션 키. `RANDOM` 모드면 매번 다른 UUID 다. */
    val partitionKey: String,
)
