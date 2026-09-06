package com.archseoul.kafkastudy.balanceworker.domain

import com.archseoul.kafkastudy.common.LedgerEvent
import com.archseoul.kafkastudy.common.PointEarnedEvent
import com.archseoul.kafkastudy.common.PointSpentEvent
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Repository
interface UserBalanceRepository : JpaRepository<UserBalance, String>

/** 이벤트 하나를 잔액에 적용한 결과. */
sealed interface ApplyResult {
    data class Applied(val userId: String, val before: Long, val after: Long) : ApplyResult
    data class Rejected(val userId: String, val balance: Long, val requested: Long) : ApplyResult
}

@Service
class BalanceService(
    private val repository: UserBalanceRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이벤트를 잔액에 적용한다.
     *
     * ## 순서 의존성이 여기서 생긴다
     *
     * 사용(`POINT_SPENT`) 시 **그 시점 잔액**으로 부족 여부를 판정한다.
     * 따라서 처리 순서가 바뀌면 결과가 달라진다.
     *
     * ```
     * 잔액 1,000
     * ① 5,000 적립   ② 3,000 사용
     *
     * ①→② :  1,000 → 6,000 → 3,000   ✅
     * ②→① :  1,000 → [부족, 거절] → 6,000   ❌
     * ```
     *
     * **이것이 이 프로젝트가 순서 보장을 요구하는 이유**이며,
     * 4단계에서 파티션 키를 흩뜨리면 잔액이 틀어지는 근거다.
     *
     * 멱등 처리(같은 `eventId` 를 두 번 받으면 무시)는 **5단계에서 추가한다.**
     * 재생 시 포인트가 두 배가 되는 문제를 먼저 겪어야 필요성이 몸에 남는다.
     */
    @Transactional
    fun apply(event: LedgerEvent): ApplyResult {
        val entity = repository.findById(event.userId).orElseGet {
            UserBalance(userId = event.userId)
        }
        val before = entity.balance

        val result = when (event) {
            is PointEarnedEvent -> {
                entity.balance = before + event.amount
                entity.appliedEvents += 1
                ApplyResult.Applied(event.userId, before, entity.balance)
            }

            is PointSpentEvent -> {
                if (before < event.amount) {
                    // 잔액을 바꾸지 않지만 흔적은 남긴다.
                    entity.rejectedEvents += 1
                    log.warn(
                        "거절 userId={} 잔액={} 요청={} (부족)",
                        event.userId, before, event.amount,
                    )
                    ApplyResult.Rejected(event.userId, before, event.amount)
                } else {
                    entity.balance = before - event.amount
                    entity.appliedEvents += 1
                    ApplyResult.Applied(event.userId, before, entity.balance)
                }
            }
        }

        entity.updatedAt = Instant.now()
        repository.save(entity)
        return result
    }

    @Transactional(readOnly = true)
    fun all(): List<UserBalance> = repository.findAll().sortedBy { it.userId }

    /**
     * 잔액 전체를 비운다. `rebuild` 에서만 쓴다.
     *
     * **로그가 원본이므로 이것을 지워도 복원할 수 있다.**
     * 이 한 줄이 `rebuild`(정확히 복원)와 `replay`(두 배가 됨)를 가른다.
     */
    @Transactional
    fun clearAll() {
        repository.deleteAll()
        log.warn("잔액 테이블을 비웠습니다. 로그로부터 재구축됩니다.")
    }
}
