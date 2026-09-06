package com.archseoul.kafkastudy.balanceworker.consume

import com.archseoul.kafkastudy.balanceworker.domain.WorkerAssignment
import com.archseoul.kafkastudy.balanceworker.domain.WorkerAssignmentRepository
import java.time.Instant
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 파티션 배정 현황을 DB 에 기록해 화면이 볼 수 있게 한다.
 *
 * ## `ConsumerRebalanceListener` 의 두 훅
 *
 * | 콜백 | 시점 |
 * |---|---|
 * | `onPartitionsRevoked` | 소유권을 **넘기기 직전** — 정리할 기회 |
 * | `onPartitionsAssigned` | 새로 **배정받은 직후** |
 *
 * 직접 만들면 멤버십 관리 · 하트비트 · lease · fencing token 을 전부 짜야 하는 일을
 * Kafka 는 이 두 콜백으로 제공한다.
 */
@Component
class AssignmentTracker(
    private val repository: WorkerAssignmentRepository,
    @Value("\${server.port}") private val port: String,
) : ConsumerAwareRebalanceListener {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 워커 식별자. 포트로 구분한다 (예: `w-8082`). */
    val workerId: String = "w-$port"

    @Volatile
    private var current: Set<Int> = emptySet()

    override fun onPartitionsAssigned(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>,
    ) {
        current = partitions.map { it.partition() }.toSet()
        log.info("파티션 배정됨 worker={} partitions={}", workerId, current.sorted())
        save(bumpGeneration = true)
    }

    override fun onPartitionsRevokedBeforeCommit(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>,
    ) {
        // 정상 종료나 리밸런싱 직전에 불린다.
        // kill -9 로 죽이면 이 콜백은 불리지 않는다 — 그래서 하트비트가 필요하다.
        log.info("파티션 반납 worker={} partitions={}", workerId, partitions.map { it.partition() }.sorted())
        current = emptySet()
        save(bumpGeneration = false)
    }

    /**
     * 생존 신호.
     *
     * 이 값이 갱신되지 않으면 화면에서 죽은 워커로 표시된다.
     */
    @Scheduled(fixedDelay = 2_000)
    fun heartbeat() {
        save(bumpGeneration = false)
    }

    @Transactional
    fun save(bumpGeneration: Boolean) {
        val entity = repository.findById(workerId).orElseGet { WorkerAssignment(workerId = workerId) }
        entity.partitions = current.sorted().joinToString(",")
        if (bumpGeneration) entity.generation += 1
        entity.lastHeartbeat = Instant.now()
        repository.save(entity)
    }
}
