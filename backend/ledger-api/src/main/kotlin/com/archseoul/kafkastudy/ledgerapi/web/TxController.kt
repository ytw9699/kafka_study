package com.archseoul.kafkastudy.ledgerapi.web

import com.archseoul.kafkastudy.common.LedgerEvent
import com.archseoul.kafkastudy.common.PointEarnedEvent
import com.archseoul.kafkastudy.common.PointSpentEvent
import com.archseoul.kafkastudy.ledgerapi.config.PartitionKeyMode
import com.archseoul.kafkastudy.ledgerapi.config.PartitionKeyModeHolder
import com.archseoul.kafkastudy.ledgerapi.publish.LedgerEventPublisher
import com.archseoul.kafkastudy.ledgerapi.publish.PublishResult
import com.archseoul.kafkastudy.ledgerapi.stream.LedgerStreamGateway
import java.time.Instant
import kotlin.random.Random
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api")
class TxController(
    private val publisher: LedgerEventPublisher,
    private val gateway: LedgerStreamGateway,
    private val keyMode: PartitionKeyModeHolder,
) {

    /**
     * 거래 발생.
     *
     * `202 Accepted` 인 것이 의도적이다 — 발행만 했을 뿐 **잔액에 반영된 것은 아니다.**
     * 반영은 `balance-worker` 가 비동기로 한다.
     */
    @PostMapping("/tx")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createTx(@RequestBody req: TxRequest): PublishResult =
        publisher.publish(req.toEvent())

    /**
     * 연속 거래 생성 (학습용).
     *
     * 순서 문제가 드러나려면 **한 사용자의 거래가 촘촘히 이어져야** 한다.
     * 손으로 하나씩 넣으면 처리 간격이 벌어져 경쟁 상태가 잘 생기지 않는다.
     *
     * 각 사용자마다 먼저 크게 적립한 뒤 여러 번 사용하게 만든다.
     * → 순서가 지켜지면 전부 성공하지만,
     *   순서가 뒤바뀌면 **사용이 적립보다 먼저 처리되어 거절**되므로 잔액이 달라진다.
     */
    @PostMapping("/tx/burst")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun burst(@RequestBody req: BurstRequest): BurstResult {
        val results = mutableListOf<PublishResult>()
        repeat(req.users) { u ->
            val userId = "user-${u + 1}"
            // 먼저 충분히 적립해두고
            results += publisher.publish(
                PointEarnedEvent(
                    userId = userId,
                    amount = req.seedAmount,
                    reason = "burst 시드 적립",
                )
            )
            // 그 다음 잘게 사용한다
            repeat(req.txPerUser) { i ->
                val amount = Random.nextLong(100, 1_000) * 10
                results += publisher.publish(
                    PointSpentEvent(userId = userId, amount = amount, reason = "burst 사용 ${i + 1}")
                )
            }
        }
        return BurstResult(
            published = results.size,
            keyMode = keyMode.get().name,
            byPartition = results.groupingBy { it.partition }.eachCount().toSortedMap(),
        )
    }

    /**
     * 파티션 키 모드 전환 (학습용) — **4단계 실험 A 의 조작 장치.**
     *
     * `RANDOM` 으로 바꾸면 같은 사용자의 거래가 여러 파티션에 흩어져
     * 병렬 워커가 순서를 뒤바꿔 처리하고, **잔액이 틀어진다.**
     */
    @PostMapping("/config/partition-key-mode")
    fun setKeyMode(@RequestBody req: KeyModeRequest): KeyModeResponse =
        KeyModeResponse(keyMode.set(req.mode))

    @GetMapping("/config/partition-key-mode")
    fun getKeyMode(): KeyModeResponse = KeyModeResponse(keyMode.get())

    /** 실시간 원장 피드. 생성·사용 두 종류를 모두 중계한다. */
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): SseEmitter = gateway.subscribe()

    @GetMapping("/stream/connections")
    fun connections(): Map<String, Int> = mapOf("count" to gateway.connectionCount())
}

// ── 요청/응답 ───────────────────────────────────────────────────────────────

enum class TxType { EARN, SPEND }

data class TxRequest(
    val userId: String,
    val type: TxType,
    val amount: Long,
    val reason: String = "",
    /** 학습용. 지정하면 그 시각으로 발행한다 (과거 데이터 만들기). */
    val occurredAt: Instant? = null,
) {
    fun toEvent(): LedgerEvent {
        require(amount > 0) { "amount 는 양수여야 한다. 부호는 type 이 결정한다." }
        val at = occurredAt ?: Instant.now()
        return when (type) {
            TxType.EARN -> PointEarnedEvent(occurredAt = at, userId = userId, amount = amount, reason = reason)
            TxType.SPEND -> PointSpentEvent(occurredAt = at, userId = userId, amount = amount, reason = reason)
        }
    }
}

data class BurstRequest(
    val users: Int = 5,
    val txPerUser: Int = 20,
    /** 각 사용자에게 먼저 넣어줄 적립액 */
    val seedAmount: Long = 100_000,
)

data class BurstResult(
    val published: Int,
    val keyMode: String,
    /** 파티션별 적재 건수. `USER_ID` 모드면 사용자 수만큼의 파티션에만 몰린다. */
    val byPartition: Map<Int, Int>,
)

data class KeyModeRequest(val mode: PartitionKeyMode)
data class KeyModeResponse(val mode: PartitionKeyMode)
