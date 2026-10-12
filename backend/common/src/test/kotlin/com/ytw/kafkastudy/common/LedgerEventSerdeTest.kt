package com.ytw.kafkastudy.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.databind.SerializationFeature
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 컨슈머는 하나의 토픽에서 두 종류의 이벤트를 받는다.
 * `eventType` 만 보고 올바른 타입으로 복원되어야 분기 처리를 할 수 있다.
 */
class LedgerEventSerdeTest {

    private val mapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        // Instant 를 다루려면 필수. 없으면 직렬화 자체가 실패한다.
        .registerModule(JavaTimeModule())
        // 기본값은 epoch 숫자로 쓰는 것인데, Kafka UI 에서 사람이 읽을 수 있게 ISO-8601 로 둔다.
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `적립 이벤트가 왕복 직렬화된다`() {
        val origin = PointEarnedEvent(userId = "user-42", amount = 5_000, reason = "주문 적립")

        val json = mapper.writeValueAsString(origin)
        val restored = mapper.readValue(json, LedgerEvent::class.java)

        assertIs<PointEarnedEvent>(restored)
        assertEquals(origin, restored)
    }

    @Test
    fun `사용 이벤트가 왕복 직렬화된다`() {
        val origin = PointSpentEvent(userId = "user-42", amount = 3_000, reason = "상품 구매")

        val json = mapper.writeValueAsString(origin)
        val restored = mapper.readValue(json, LedgerEvent::class.java)

        assertIs<PointSpentEvent>(restored)
        assertEquals(origin, restored)
    }

    @Test
    fun `eventType 만 보고 올바른 구현체로 역직렬화된다`() {
        val earnedJson = """
            {
              "eventId": "9f1c2e7a-4b8d-4d5e-9a1f-2c3b4d5e6f70",
              "eventType": "POINT_EARNED",
              "occurredAt": "2026-09-05T14:32:10.123Z",
              "userId": "user-42",
              "amount": 5000,
              "reason": "주문 적립"
            }
        """.trimIndent()

        val event = mapper.readValue(earnedJson, LedgerEvent::class.java)

        assertIs<PointEarnedEvent>(event)
        assertEquals(UUID.fromString("9f1c2e7a-4b8d-4d5e-9a1f-2c3b4d5e6f70"), event.eventId)
        assertEquals(Instant.parse("2026-09-05T14:32:10.123Z"), event.occurredAt)
        assertEquals("user-42", event.userId)
        assertEquals(5_000, event.amount)
    }

    @Test
    fun `occurredAt 이 ISO-8601 문자열로 직렬화된다`() {
        val event = PointEarnedEvent(
            occurredAt = Instant.parse("2026-09-05T14:32:10.123Z"),
            userId = "user-42",
            amount = 5_000,
        )

        val json = mapper.writeValueAsString(event)

        // epoch 숫자가 아니라 사람이 읽을 수 있는 형태여야 Kafka UI 에서 확인하기 좋다.
        assertTrue(json.contains("\"occurredAt\":\"2026-09-05T14:32:10.123Z\""), json)
    }

    @Test
    fun `signedAmount 는 적립이 양수 사용이 음수다`() {
        assertEquals(5_000, PointEarnedEvent(userId = "u", amount = 5_000).signedAmount)
        assertEquals(-3_000, PointSpentEvent(userId = "u", amount = 3_000).signedAmount)
    }
}
