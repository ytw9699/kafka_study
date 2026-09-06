package com.archseoul.kafkastudy.balanceworker

import com.archseoul.kafkastudy.balanceworker.domain.ApplyResult
import com.archseoul.kafkastudy.balanceworker.domain.BalanceService
import com.archseoul.kafkastudy.balanceworker.domain.UserBalance
import com.archseoul.kafkastudy.balanceworker.domain.ProcessedEvent
import com.archseoul.kafkastudy.balanceworker.domain.ProcessedEventRepository
import com.archseoul.kafkastudy.balanceworker.domain.UserBalanceRepository
import com.archseoul.kafkastudy.common.PointEarnedEvent
import com.archseoul.kafkastudy.common.PointSpentEvent
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** DB 없이 도메인 로직만 검증하기 위한 인메모리 구현. */
private class FakeRepository : UserBalanceRepository {
    val store = mutableMapOf<String, UserBalance>()

    override fun findById(id: String): Optional<UserBalance> = Optional.ofNullable(store[id])
    override fun <S : UserBalance> save(entity: S): S = entity.also { store[it.userId] = it }
    override fun findAll(): List<UserBalance> = store.values.toList()

    // 아래는 이 테스트에서 쓰지 않는다.
    override fun findAll(sort: org.springframework.data.domain.Sort) = notUsed()
    override fun findAll(pageable: org.springframework.data.domain.Pageable) = notUsed()
    override fun <S : UserBalance> saveAll(entities: MutableIterable<S>) = notUsed()
    override fun findAllById(ids: MutableIterable<String>) = notUsed()
    override fun count() = notUsed()
    override fun deleteById(id: String) = notUsed()
    override fun delete(entity: UserBalance) = notUsed()
    override fun deleteAllById(ids: MutableIterable<String>) = notUsed()
    override fun deleteAll(entities: MutableIterable<UserBalance>) = notUsed()
    override fun deleteAll() = notUsed()
    override fun existsById(id: String) = notUsed()
    override fun flush() = notUsed()
    override fun <S : UserBalance> saveAndFlush(entity: S) = notUsed()
    override fun <S : UserBalance> saveAllAndFlush(entities: MutableIterable<S>) = notUsed()
    override fun deleteAllInBatch(entities: MutableIterable<UserBalance>) = notUsed()
    override fun deleteAllByIdInBatch(ids: MutableIterable<String>) = notUsed()
    override fun deleteAllInBatch() = notUsed()
    override fun getOne(id: String) = notUsed()
    override fun getById(id: String) = notUsed()
    override fun getReferenceById(id: String) = notUsed()
    override fun <S : UserBalance> findOne(example: org.springframework.data.domain.Example<S>) = notUsed()
    override fun <S : UserBalance> findAll(example: org.springframework.data.domain.Example<S>) = notUsed()
    override fun <S : UserBalance> findAll(example: org.springframework.data.domain.Example<S>, sort: org.springframework.data.domain.Sort) = notUsed()
    override fun <S : UserBalance> findAll(example: org.springframework.data.domain.Example<S>, pageable: org.springframework.data.domain.Pageable) = notUsed()
    override fun <S : UserBalance> count(example: org.springframework.data.domain.Example<S>) = notUsed()
    override fun <S : UserBalance> exists(example: org.springframework.data.domain.Example<S>) = notUsed()
    override fun <S : UserBalance, R : Any> findBy(
        example: org.springframework.data.domain.Example<S>,
        queryFunction: java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>,
    ) = notUsed()

    private fun notUsed(): Nothing = throw UnsupportedOperationException("테스트에서 사용하지 않음")
}

/** 멱등 기록도 인메모리로 흉내낸다. */
private class FakeProcessedRepository : ProcessedEventRepository {
    val store = mutableMapOf<UUID, ProcessedEvent>()

    override fun existsById(id: UUID): Boolean = store.containsKey(id)
    override fun <S : ProcessedEvent> save(entity: S): S = entity.also { store[it.eventId] = it }
    override fun count(): Long = store.size.toLong()
    override fun deleteAll() { store.clear() }
    override fun findById(id: UUID): Optional<ProcessedEvent> = Optional.ofNullable(store[id])
    override fun findAll(): List<ProcessedEvent> = store.values.toList()

    private fun notUsed(): Nothing = throw UnsupportedOperationException("테스트에서 사용하지 않음")
    override fun findAll(sort: org.springframework.data.domain.Sort) = notUsed()
    override fun findAll(pageable: org.springframework.data.domain.Pageable) = notUsed()
    override fun <S : ProcessedEvent> saveAll(entities: MutableIterable<S>) = notUsed()
    override fun findAllById(ids: MutableIterable<UUID>) = notUsed()
    override fun deleteById(id: UUID) = notUsed()
    override fun delete(entity: ProcessedEvent) = notUsed()
    override fun deleteAllById(ids: MutableIterable<UUID>) = notUsed()
    override fun deleteAll(entities: MutableIterable<ProcessedEvent>) = notUsed()
    override fun flush() = notUsed()
    override fun <S : ProcessedEvent> saveAndFlush(entity: S) = notUsed()
    override fun <S : ProcessedEvent> saveAllAndFlush(entities: MutableIterable<S>) = notUsed()
    override fun deleteAllInBatch(entities: MutableIterable<ProcessedEvent>) = notUsed()
    override fun deleteAllByIdInBatch(ids: MutableIterable<UUID>) = notUsed()
    override fun deleteAllInBatch() = notUsed()
    override fun getOne(id: UUID) = notUsed()
    override fun getById(id: UUID) = notUsed()
    override fun getReferenceById(id: UUID) = notUsed()
    override fun <S : ProcessedEvent> findOne(example: org.springframework.data.domain.Example<S>) = notUsed()
    override fun <S : ProcessedEvent> findAll(example: org.springframework.data.domain.Example<S>) = notUsed()
    override fun <S : ProcessedEvent> findAll(example: org.springframework.data.domain.Example<S>, sort: org.springframework.data.domain.Sort) = notUsed()
    override fun <S : ProcessedEvent> findAll(example: org.springframework.data.domain.Example<S>, pageable: org.springframework.data.domain.Pageable) = notUsed()
    override fun <S : ProcessedEvent> count(example: org.springframework.data.domain.Example<S>) = notUsed()
    override fun <S : ProcessedEvent> exists(example: org.springframework.data.domain.Example<S>) = notUsed()
    override fun <S : ProcessedEvent, R : Any> findBy(
        example: org.springframework.data.domain.Example<S>,
        queryFunction: java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>,
    ) = notUsed()
}

class BalanceServiceTest {

    private val repo = FakeRepository()
    private val service = BalanceService(repo, FakeProcessedRepository(), idempotent = false)

    @Test
    fun `적립은 잔액을 늘린다`() {
        val r = service.apply(PointEarnedEvent(userId = "u1", amount = 5_000))

        assertIs<ApplyResult.Applied>(r)
        assertEquals(0, r.before)
        assertEquals(5_000, r.after)
        assertEquals(1, repo.store["u1"]!!.appliedEvents)
    }

    @Test
    fun `사용은 잔액을 줄인다`() {
        service.apply(PointEarnedEvent(userId = "u1", amount = 5_000))

        val r = service.apply(PointSpentEvent(userId = "u1", amount = 3_000))

        assertIs<ApplyResult.Applied>(r)
        assertEquals(2_000, r.after)
    }

    @Test
    fun `잔액이 부족하면 거절되고 잔액은 변하지 않는다`() {
        service.apply(PointEarnedEvent(userId = "u1", amount = 1_000))

        val r = service.apply(PointSpentEvent(userId = "u1", amount = 3_000))

        assertIs<ApplyResult.Rejected>(r)
        assertEquals(1_000, r.balance)
        assertEquals(3_000, r.requested)

        val entity = repo.store["u1"]!!
        assertEquals(1_000, entity.balance, "거절되면 잔액이 변하지 않아야 한다")
        assertEquals(1, entity.rejectedEvents, "거절은 흔적을 남겨야 한다")
        assertEquals(1, entity.appliedEvents, "거절은 applied 로 세지 않는다")
    }

    /**
     * **이 프로젝트의 핵심 성질.**
     *
     * 같은 두 이벤트를 순서만 바꿔 적용하면 결과가 달라진다.
     * 거절 판정이 그 시점 잔액에 좌우되기 때문이다.
     *
     * 4단계에서 파티션 키를 흩뜨리면 병렬 워커가 이 순서를 뒤바꾸고, 잔액이 틀어진다.
     */
    @Test
    fun `순서가 바뀌면 결과가 달라진다`() {
        val earn = PointEarnedEvent(userId = "u1", amount = 5_000)
        val spend = PointSpentEvent(userId = "u1", amount = 3_000)

        // ① 적립 → ② 사용
        val forward = BalanceService(FakeRepository(), FakeProcessedRepository(), false).let { s ->
            s.apply(earn); s.apply(spend)
            s.all().first().balance
        }

        // ② 사용 → ① 적립
        val reversed = BalanceService(FakeRepository(), FakeProcessedRepository(), false).let { s ->
            s.apply(spend); s.apply(earn)
            s.all().first().balance
        }

        assertEquals(2_000, forward, "적립 후 사용하면 2,000 이 남는다")
        assertEquals(5_000, reversed, "사용이 먼저 오면 거절되어 5,000 이 남는다")
    }

    // ── 멱등 처리 (5단계) ────────────────────────────────────────────────

    @Test
    fun `멱등 처리가 꺼져 있으면 같은 이벤트가 두 번 적용된다`() {
        val svc = BalanceService(FakeRepository(), FakeProcessedRepository(), idempotent = false)
        val event = PointEarnedEvent(userId = "u1", amount = 5_000)

        svc.apply(event)
        svc.apply(event)   // 같은 eventId 를 다시

        assertEquals(10_000, svc.all().first().balance, "방어가 없으면 두 배가 된다")
    }

    @Test
    fun `멱등 처리가 켜져 있으면 같은 이벤트를 건너뛴다`() {
        val svc = BalanceService(FakeRepository(), FakeProcessedRepository(), idempotent = true)
        val event = PointEarnedEvent(userId = "u1", amount = 5_000)

        svc.apply(event)
        val second = svc.apply(event)

        assertIs<ApplyResult.Skipped>(second)
        assertEquals(5_000, svc.all().first().balance, "두 번째는 반영되지 않아야 한다")
    }

    @Test
    fun `거절된 이벤트도 처리 기록에 남는다`() {
        // 거절 역시 '처리한 결과'다. 재생 시 거절 카운트가 다시 늘어나면 안 된다.
        val processed = FakeProcessedRepository()
        val svc = BalanceService(FakeRepository(), processed, idempotent = true)
        val spend = PointSpentEvent(userId = "u1", amount = 3_000)

        assertIs<ApplyResult.Rejected>(svc.apply(spend))
        assertIs<ApplyResult.Skipped>(svc.apply(spend))
        assertEquals(1, processed.count())
    }

    @Test
    fun `금액이 같아도 eventId 가 다르면 별개로 처리된다`() {
        // 같은 사용자가 같은 금액을 두 번 적립하는 것은 정상이다.
        // 내용 기반 키로는 이것을 중복과 구분할 수 없다.
        val svc = BalanceService(FakeRepository(), FakeProcessedRepository(), idempotent = true)

        svc.apply(PointEarnedEvent(userId = "u1", amount = 5_000))
        svc.apply(PointEarnedEvent(userId = "u1", amount = 5_000))   // eventId 가 다르다

        assertEquals(10_000, svc.all().first().balance)
    }

    @Test
    fun `사용자별로 잔액이 독립적이다`() {
        service.apply(PointEarnedEvent(userId = "u1", amount = 5_000))
        service.apply(PointEarnedEvent(userId = "u2", amount = 7_000))

        assertEquals(5_000, repo.store["u1"]!!.balance)
        assertEquals(7_000, repo.store["u2"]!!.balance)
    }
}
