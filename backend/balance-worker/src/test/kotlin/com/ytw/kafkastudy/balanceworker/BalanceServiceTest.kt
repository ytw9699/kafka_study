package com.ytw.kafkastudy.balanceworker

import com.ytw.kafkastudy.balanceworker.domain.ApplyResult
import com.ytw.kafkastudy.balanceworker.domain.BalanceService
import com.ytw.kafkastudy.balanceworker.domain.UserBalance
import com.ytw.kafkastudy.balanceworker.domain.UserBalanceRepository
import com.ytw.kafkastudy.common.PointEarnedEvent
import com.ytw.kafkastudy.common.PointSpentEvent
import java.util.Optional
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

class BalanceServiceTest {

    private val repo = FakeRepository()
    private val service = BalanceService(repo)

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
        val forward = BalanceService(FakeRepository()).let { s ->
            s.apply(earn); s.apply(spend)
            s.all().first().balance
        }

        // ② 사용 → ① 적립
        val reversed = BalanceService(FakeRepository()).let { s ->
            s.apply(spend); s.apply(earn)
            s.all().first().balance
        }

        assertEquals(2_000, forward, "적립 후 사용하면 2,000 이 남는다")
        assertEquals(5_000, reversed, "사용이 먼저 오면 거절되어 5,000 이 남는다")
    }

    @Test
    fun `사용자별로 잔액이 독립적이다`() {
        service.apply(PointEarnedEvent(userId = "u1", amount = 5_000))
        service.apply(PointEarnedEvent(userId = "u2", amount = 7_000))

        assertEquals(5_000, repo.store["u1"]!!.balance)
        assertEquals(7_000, repo.store["u2"]!!.balance)
    }
}
