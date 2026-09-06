package com.archseoul.kafkastudy.balanceworker.web

import com.archseoul.kafkastudy.balanceworker.domain.WorkerAssignmentRepository
import java.time.Duration
import java.time.Instant
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/workers")
class WorkerController(
    private val repository: WorkerAssignmentRepository,
) {
    /**
     * 워커별 파티션 배정 현황. **리밸런싱이 여기서 눈에 보인다.**
     *
     * 어느 인스턴스에 물어도 같은 DB 를 보므로 결과가 동일하다.
     */
    @GetMapping
    fun workers(): WorkersResponse {
        val now = Instant.now()
        val rows = repository.findAll()
            .sortedBy { it.workerId }
            .map {
                val silence = Duration.between(it.lastHeartbeat, now)
                WorkerRow(
                    workerId = it.workerId,
                    partitions = it.partitions.split(",").filter { p -> p.isNotBlank() }.map(String::toInt),
                    generation = it.generation,
                    lastHeartbeat = it.lastHeartbeat.toString(),
                    secondsSinceHeartbeat = silence.seconds,
                    // 하트비트가 2초 주기이므로 8초 넘게 소식이 없으면 죽은 것으로 본다.
                    alive = silence.seconds < 8,
                )
            }
        return WorkersResponse(
            workers = rows,
            aliveCount = rows.count { it.alive },
            assignedPartitions = rows.filter { it.alive }.flatMap { it.partitions }.distinct().sorted(),
        )
    }
}

data class WorkersResponse(
    val workers: List<WorkerRow>,
    val aliveCount: Int,
    /** 살아 있는 워커들이 실제로 담당 중인 파티션 */
    val assignedPartitions: List<Int>,
)

data class WorkerRow(
    val workerId: String,
    val partitions: List<Int>,
    val generation: Int,
    val lastHeartbeat: String,
    val secondsSinceHeartbeat: Long,
    /** 하트비트가 최근인가. `kill -9` 로 죽으면 배정 기록은 남지만 이 값이 false 가 된다. */
    val alive: Boolean,
)
