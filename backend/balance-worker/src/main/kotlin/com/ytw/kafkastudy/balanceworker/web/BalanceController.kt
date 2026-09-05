package com.ytw.kafkastudy.balanceworker.web

import com.ytw.kafkastudy.balanceworker.domain.BalanceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/balance")
class BalanceController(
    private val balanceService: BalanceService,
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
        )
    }
}

data class BalancesResponse(
    val balances: List<BalanceRow>,
    val totalApplied: Int,
    val totalRejected: Int,
)

data class BalanceRow(
    val userId: String,
    val balance: Long,
    val appliedEvents: Int,
    /** 잔액 부족으로 거절된 사용 건수 */
    val rejectedEvents: Int,
    val updatedAt: String,
)
