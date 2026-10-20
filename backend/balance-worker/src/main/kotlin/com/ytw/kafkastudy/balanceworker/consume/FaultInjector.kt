package com.ytw.kafkastudy.balanceworker.consume

import com.ytw.kafkastudy.common.LedgerEvent
import java.time.Instant
import org.springframework.stereotype.Component

/**
 * 실험용 실패 장치 — **거래의 사유(reason) 칸에 표시를 넣으면 처리 중 예외를 던진다.**
 *
 * | 사유 | 동작 | 흉내 내는 실무 상황 |
 * |---|---|---|
 * | `[실험] 일시 실패 5초` | 거래 발생 후 5초 동안만 실패 | DB · 외부 API 가 잠깐 응답하지 않음 |
 * | `[실험] 영구 실패` | 몇 번을 다시 해도 실패 | 잘못된 데이터 — 다시 해도 소용없음 |
 *
 * ## 왜 사유 칸인가
 *
 * 워커가 여러 개여도 **메시지 자체에 표시가 있으므로 모두 같은 규칙으로 실패**한다.
 * 워커마다 설정을 따로 바꿀 필요가 없고, 화면의 사유 입력칸만으로 실험할 수 있다.
 *
 * 일시 실패를 "N번 실패"가 아니라 **"발생 후 N초 동안 실패"** 로 만든 이유도 같다.
 * 횟수를 세려면 어느 워커가 몇 번 시도했는지 공유해야 하지만, 시간은 모두가 같은 기준을 본다.
 */
@Component
class FaultInjector {

    fun check(event: LedgerEvent) {
        val reason = event.reason
        if (reason.startsWith(PERMANENT)) {
            throw PermanentFailureException("영구 실패 표시가 있는 거래 eventId=${event.eventId}")
        }
        TRANSIENT.find(reason)?.let {
            val seconds = it.groupValues[1].toLong()
            val until = event.occurredAt.plusSeconds(seconds)
            if (Instant.now().isBefore(until)) {
                throw TransientFailureException("일시 실패 중 (${seconds}초) eventId=${event.eventId}")
            }
        }
    }

    companion object {
        const val PERMANENT = "[실험] 영구 실패"
        private val TRANSIENT = Regex("""^\[실험] 일시 실패 (\d+)초""")
    }
}

/** 잠깐 기다리면 성공할 수 있는 실패. **다시 시도할 가치가 있다.** */
class TransientFailureException(message: String) : RuntimeException(message)

/** 다시 해도 소용없는 실패. **빨리 DLT 로 빼야 한다.** */
class PermanentFailureException(message: String) : RuntimeException(message)

/**
 * 이미 보류된 사용자의 거래.
 *
 * 앞선 거래가 DLT 로 빠진 사용자의 뒤 거래를 그대로 처리하면 **건너뛴 거래만큼 잔액이 틀어진다.**
 * 그래서 뒤 거래도 함께 DLT 로 보내 그 사용자를 통째로 멈춘다. 다른 사용자는 계속 흐른다.
 */
class UserQuarantinedException(message: String) : RuntimeException(message)
