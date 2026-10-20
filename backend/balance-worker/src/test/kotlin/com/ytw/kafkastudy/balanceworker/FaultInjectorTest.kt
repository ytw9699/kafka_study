package com.ytw.kafkastudy.balanceworker

import com.ytw.kafkastudy.balanceworker.consume.FaultInjector
import com.ytw.kafkastudy.balanceworker.consume.PermanentFailureException
import com.ytw.kafkastudy.balanceworker.consume.TransientFailureException
import com.ytw.kafkastudy.common.PointSpentEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FaultInjectorTest {

    private val faults = FaultInjector()

    private fun spend(reason: String, occurredAt: Instant = Instant.now()) =
        PointSpentEvent(userId = "user-1", amount = 1_000, reason = reason, occurredAt = occurredAt)

    @Test
    fun `표시가 없으면 실패하지 않는다`() {
        faults.check(spend("커피"))
    }

    @Test
    fun `영구 실패 표시가 있으면 언제나 실패한다`() {
        assertFailsWith<PermanentFailureException> {
            faults.check(spend("[실험] 영구 실패", occurredAt = Instant.now().minusSeconds(3_600)))
        }
    }

    @Test
    fun `일시 실패는 정해진 시간 동안만 실패한다`() {
        assertFailsWith<TransientFailureException> {
            faults.check(spend("[실험] 일시 실패 5초"))
        }
        // 발생한 지 10초가 지났으면 성공한다
        faults.check(spend("[실험] 일시 실패 5초", occurredAt = Instant.now().minusSeconds(10)))
    }
}
