package com.archseoul.kafkastudy.balanceworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 워커별 담당 파티션. **리밸런싱을 화면에 보여주기 위한 테이블이다.**
 *
 * ## 왜 DB 에 기록하는가
 *
 * 워커는 여러 인스턴스다. 각자 메모리에 들고 있으면
 * **어느 인스턴스에 물어보느냐에 따라 답이 달라진다.**
 * 공유 DB 에 쓰면 어느 인스턴스에 물어도 전체 그림이 나온다.
 */
@Entity
@Table(name = "worker_assignment")
class WorkerAssignment(
    @Id
    @Column(name = "worker_id", nullable = false)
    val workerId: String,

    /** 담당 파티션. 쉼표 구분 (예: `0,2`). 비어 있으면 배정 없음. */
    @Column(nullable = false)
    var partitions: String = "",

    /** 리밸런싱 세대. 배정이 바뀔 때마다 증가한다. */
    @Column(nullable = false)
    var generation: Int = 0,

    /**
     * 마지막 생존 신호.
     *
     * `kill -9` 로 죽이면 `onPartitionsRevoked` 가 불리지 않아
     * **DB 에 죽은 워커의 배정이 그대로 남는다.**
     * 하트비트가 오래된 워커는 화면에서 흐리게 표시한다.
     *
     * Kafka 도 같은 방식으로 컨슈머 생존을 판단한다 (`session.timeout.ms`).
     * **분산 시스템에서 "죽었다"를 아는 유일한 방법은 "한동안 소식이 없다"뿐이다.**
     */
    @Column(name = "last_heartbeat", nullable = false)
    var lastHeartbeat: Instant = Instant.now(),
)

@Repository
interface WorkerAssignmentRepository : JpaRepository<WorkerAssignment, String>
