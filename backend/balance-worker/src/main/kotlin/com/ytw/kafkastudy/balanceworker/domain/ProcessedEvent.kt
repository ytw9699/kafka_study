package com.ytw.kafkastudy.balanceworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 이미 처리한 이벤트. **멱등성의 핵심.**
 *
 * ## 왜 `eventId` 인가
 *
 * | 후보 | 왜 안 되는가 |
 * |---|---|
 * | `userId` | 한 사용자에게 **여러 이벤트**가 발생한다 |
 * | 내용 기반 (userId + amount) | 같은 금액을 **두 번 적립**할 수 있다 |
 * | `eventId` | ✅ 이벤트마다 고유하다 |
 *
 * ## 이 방어가 막는 것과 막지 못하는 것
 *
 * **막는 것** — 컨슈머 쪽 중복 처리
 * Kafka 의 전달 보장은 at-least-once 다. 재생·리밸런싱·재기동에서
 * 같은 이벤트를 두 번 받을 수 있고, 그것을 여기서 걸러낸다.
 *
 * **막지 못하는 것** — 프로듀서 쪽 중복 발행
 * `ledger-api` 가 같은 거래를 두 번 발행하면 `eventId` 가 서로 달라 구분할 수 없다.
 * 그 경우는 **비즈니스 키**(주문번호 등)가 필요하다.
 *
 * **"멱등성"은 한 가지가 아니다.** 어느 지점의 중복을 막는지가 다르다.
 */
@Entity
@Table(name = "processed_event")
class ProcessedEvent(
    @Id
    @Column(name = "event_id", nullable = false)
    val eventId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @Column(name = "processed_at", nullable = false)
    val processedAt: Instant = Instant.now(),
)

@Repository
interface ProcessedEventRepository : JpaRepository<ProcessedEvent, UUID>
