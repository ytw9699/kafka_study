package com.ytw.kafkastudy.balanceworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 거래가 DLT 로 빠져 **처리를 보류한 사용자.**
 *
 * ## 왜 사용자 단위로 멈추는가
 *
 * 포인트 계산은 순서가 결과를 바꾼다. 한 거래를 DLT 로 빼고 그 사용자의 다음 거래를 처리하면
 * **빠진 거래를 건너뛴 잔액**이 된다.
 *
 * ```
 * ① 5,000 적립   ② 3,000 사용 (영구 실패 → DLT)   ③ 4,000 사용
 * ③ 을 그대로 처리 → 5,000 - 4,000 = 1,000  ← ② 가 없는 셈. 틀렸다
 * ```
 *
 * 그렇다고 파티션 전체를 멈추면 **같은 파티션의 무관한 사용자까지** 멈춘다.
 * 그래서 **문제 생긴 사용자만** 멈추고 나머지는 계속 흐르게 한다.
 *
 * 보류된 사용자의 거래는 모두 DLT 에 순서대로 쌓인다. 원인을 고친 뒤 DLT 를 다시 처리하면 된다.
 */
@Entity
@Table(name = "quarantined_user")
class QuarantinedUser(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: String,

    /** 처음 DLT 로 빠진 이유 */
    @Column(nullable = false, length = 500)
    val reason: String,

    @Column(name = "quarantined_at", nullable = false)
    val quarantinedAt: Instant = Instant.now(),
)

@Repository
interface QuarantinedUserRepository : JpaRepository<QuarantinedUser, String>
