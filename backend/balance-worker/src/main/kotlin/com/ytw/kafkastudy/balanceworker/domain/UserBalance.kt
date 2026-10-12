package com.ytw.kafkastudy.balanceworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 사용자별 잔액. **이벤트 로그로부터 계산된 파생 상태**다.
 *
 * 원본은 Kafka 의 `ledger-events` 이며, 이 테이블은 언제든 로그로부터 재구축할 수 있다
 * (5단계 `rebuild`).
 *
 * ## `data class` 로 만들지 않는다
 *
 * `data class` 가 생성하는 `equals`/`hashCode` 는 모든 프로퍼티를 읽는데,
 * lazy 프록시를 만나면 초기화를 유발하거나 예외가 난다.
 * `data class` 는 `common` 의 이벤트 DTO 전용이다.
 */
@Entity
@Table(name = "user_balance")
class UserBalance(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(nullable = false)
    var balance: Long = 0,

    /** 실제로 잔액에 반영된 이벤트 수 */
    @Column(name = "applied_events", nullable = false)
    var appliedEvents: Int = 0,

    /** 잔액 부족으로 거절된 사용 이벤트 수. 반영은 안 되지만 흔적은 남긴다. */
    @Column(name = "rejected_events", nullable = false)
    var rejectedEvents: Int = 0,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
