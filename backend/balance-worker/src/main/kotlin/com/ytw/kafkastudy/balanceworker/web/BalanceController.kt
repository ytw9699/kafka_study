package com.ytw.kafkastudy.balanceworker.web

import com.ytw.kafkastudy.balanceworker.config.ErrorMode
import com.ytw.kafkastudy.balanceworker.consume.ReplayCoordinator
import com.ytw.kafkastudy.balanceworker.domain.QuarantineService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import com.ytw.kafkastudy.balanceworker.domain.BalanceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/balance")
class BalanceController(
    private val balanceService: BalanceService,
    private val replay: ReplayCoordinator,
    private val quarantine: QuarantineService,
    @Value("\${app.error-mode}") private val errorMode: ErrorMode,
) {
    /**
     * 사용자별 잔액.
     *
     * 워커 인스턴스가 여러 개여도 **같은 DB 를 보므로 어느 인스턴스에 물어도 같다.**
     */
    @GetMapping
    fun balances(): BalancesResponse {
        val rows = balanceService.all().map {
            BalanceRow(
                userId = it.userId,
                balance = it.balance,
                appliedEvents = it.appliedEvents,
                rejectedEvents = it.rejectedEvents,
                updatedAt = it.updatedAt.toString(),
            )
        }
        return BalancesResponse(
            balances = rows,
            totalApplied = rows.sumOf { it.appliedEvents },
            totalRejected = rows.sumOf { it.rejectedEvents },
            idempotent = balanceService.isIdempotent(),
            processedEvents = balanceService.processedCount(),
            errorMode = errorMode.name,
            quarantinedUsers = quarantine.all().map { QuarantinedRow(it.userId, it.reason) },
        )
    }

    /**
     * 재시도 토픽 모드에서는 재시도 토픽을 읽는 컨슈머도 같은 리스너를 써서
     * 되감기 콜백이 어느 컨슈머 것인지 섞인다. 이 모드에서는 재생을 막는다.
     */
    private fun requireReplayable() {
        if (errorMode == ErrorMode.NON_BLOCKING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "NON_BLOCKING 모드에서는 재생을 지원하지 않습니다")
        }
    }

    /**
     * **전체 재구축** — 잔액을 비우고 처음부터 다시 읽는다.
     *
     * 실무의 "집계 로직이 바뀌어 전체 재계산이 필요하다"에 해당한다.
     * 과거 계산이 통째로 무효이므로 비우고 다시 쌓는 것이 맞다.
     */
    @PostMapping("/rebuild")
    fun rebuild(): ReplayResponse {
        requireReplayable()
        balanceService.clearAll()
        // 보류도 함께 푼다. 처음부터 다시 읽으면 실패할 거래는 다시 실패해 다시 보류된다.
        quarantine.clear()
        val partitions = replay.seekToBeginning()
        return ReplayResponse(
            mode = "REBUILD",
            clearedBalances = true,
            partitions = partitions.map { it.partition() }.sorted(),
            note = "잔액을 비우고 offset 을 0으로 되감았습니다. 정확히 복원됩니다.",
        )
    }

    /**
     * **재생** — 잔액을 그대로 둔 채 offset 만 되감는다.
     *
     * 멱등 처리가 없으면 같은 이벤트가 두 번 적용되어 **잔액이 두 배가 된다.**
     * 5단계 실험은 이 동작으로 한다.
     *
     * ### 왜 CLI `--reset-offsets` 가 아니라 API 인가
     *
     * `kafka-consumer-groups --reset-offsets` 는 **활성 컨슈머가 있으면 거부된다.**
     * 워커를 내려야 하는데, 그러면 재생 시작 시점에 워커가 방금 뜬 상태라
     * 화면에서 "두 배로 뛰는 순간"을 볼 수 없다.
     */
    @PostMapping("/replay")
    fun replayAll(): ReplayResponse {
        requireReplayable()
        val partitions = replay.seekToBeginning()
        return ReplayResponse(
            mode = "REPLAY",
            clearedBalances = false,
            partitions = partitions.map { it.partition() }.sorted(),
            note = "잔액을 유지한 채 offset 만 되감았습니다. 멱등 처리가 없으면 두 배가 됩니다.",
        )
    }
}

data class ReplayResponse(
    val mode: String,
    val clearedBalances: Boolean,
    /** 이 워커가 되감은 파티션. 워커가 여러 개면 각자에게 호출해야 한다. */
    val partitions: List<Int>,
    val note: String,
)

data class BalancesResponse(
    val balances: List<BalanceRow>,
    val totalApplied: Int,
    val totalRejected: Int,
    /** 멱등 처리 활성화 여부. `app.idempotent` */
    val idempotent: Boolean,
    /** `processed_event` 에 기록된 건수 */
    val processedEvents: Long,
    /** 실패 처리 모드. `app.error-mode` */
    val errorMode: String,
    /** 거래가 DLT 로 빠져 처리를 보류한 사용자 */
    val quarantinedUsers: List<QuarantinedRow>,
)

data class QuarantinedRow(val userId: String, val reason: String)

data class BalanceRow(
    val userId: String,
    val balance: Long,
    val appliedEvents: Int,
    /** 잔액 부족으로 거절된 사용 건수 */
    val rejectedEvents: Int,
    val updatedAt: String,
)
