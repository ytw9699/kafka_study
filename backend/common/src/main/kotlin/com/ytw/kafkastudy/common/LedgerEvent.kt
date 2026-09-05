package com.ytw.kafkastudy.common

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.UUID

/**
 * `ledger-events` 토픽에 흐르는 이벤트.
 *
 * ## 설계 전제
 *
 * **Kafka 가 원장의 원본(source of truth)이다.** 잔액은 이 이벤트들을 순서대로 적용한 파생 상태일 뿐이다.
 *
 * ### 이벤트에 "잔액"을 담지 않는다
 *
 * 이벤트는 **일어난 사실**만 담는다. 발행 시점에 잔액을 계산해 담으면
 * 발행자가 그 사용자의 이전 거래를 모두 알아야 하므로 **병렬 처리가 불가능**해진다.
 *
 * ### 메시지 키는 [userId]
 *
 * 같은 사용자의 거래가 같은 파티션에 들어가야 순서가 보장된다.
 * Kafka 는 **파티션 내부에서만** 순서를 보장하기 때문이다.
 *
 * 순서가 뒤바뀌면 잔액 부족 판정이 달라져 **잔액이 틀어진다.**
 * (4단계 실험 A 에서 이것을 일부러 재현한다)
 *
 * ### 멱등 키는 [eventId]
 *
 * 한 사용자에게 여러 이벤트가 발생하므로 `userId` 로는 중복을 판별할 수 없고,
 * 같은 금액을 두 번 적립할 수도 있으므로 내용 기반 키도 안전하지 않다.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "eventType",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = PointEarnedEvent::class, name = LedgerEventType.POINT_EARNED),
    JsonSubTypes.Type(value = PointSpentEvent::class, name = LedgerEventType.POINT_SPENT),
)
sealed interface LedgerEvent {
    /** 이벤트 고유 식별자. **멱등 처리의 기준 키.** */
    val eventId: UUID

    /** `POINT_EARNED` | `POINT_SPENT` */
    val eventType: String

    /** 이벤트 발생 시각 */
    val occurredAt: Instant

    /** **파티션 키.** 같은 사용자의 거래는 같은 파티션으로 간다. */
    val userId: String

    /** 금액. 항상 양수이며, 부호는 [eventType] 이 결정한다. */
    val amount: Long

    /** 표시용 사유 */
    val reason: String
}

object LedgerEventType {
    const val POINT_EARNED = "POINT_EARNED"
    const val POINT_SPENT = "POINT_SPENT"
}

/** 포인트 적립. 잔액에 [amount] 를 더한다. */
data class PointEarnedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val eventType: String = LedgerEventType.POINT_EARNED,
    override val occurredAt: Instant = Instant.now(),
    override val userId: String,
    override val amount: Long,
    override val reason: String = "",
) : LedgerEvent

/**
 * 포인트 사용. 잔액에서 [amount] 를 뺀다.
 *
 * 잔액이 부족하면 컨슈머가 **거절**한다.
 * 이 판정이 그 시점 잔액에 좌우되므로 **순서가 바뀌면 결과가 달라진다.**
 * 이 프로젝트가 순서 보장을 요구하는 이유가 바로 이것이다.
 */
data class PointSpentEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val eventType: String = LedgerEventType.POINT_SPENT,
    override val occurredAt: Instant = Instant.now(),
    override val userId: String,
    override val amount: Long,
    override val reason: String = "",
) : LedgerEvent

/** 잔액에 적용할 증감분. 적립은 `+`, 사용은 `-`. */
val LedgerEvent.signedAmount: Long
    get() = when (this) {
        is PointEarnedEvent -> amount
        is PointSpentEvent -> -amount
    }
