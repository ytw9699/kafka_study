import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { LedgerEvent } from '../api/types'

/** 화면에 유지할 최대 이벤트 수. 이보다 오래된 것은 버린다. */
const MAX_FEED = 300

export const useLedgerStore = defineStore('ledger', () => {
  /** SSE 로 받은 이벤트. 최신이 앞. */
  const events = ref<LedgerEvent[]>([])

  function push(event: LedgerEvent) {
    events.value.unshift(event)
    if (events.value.length > MAX_FEED) {
      events.value.length = MAX_FEED
    }
  }

  function clear() {
    events.value = []
  }

  /**
   * **기대 잔액** — 프론트가 SSE 로 받은 이벤트를 스스로 계산한 값.
   *
   * `balance-worker` 가 계산한 실제 잔액과 나란히 표시한다.
   * 3단계에서는 둘이 일치하지만, **4단계에서 어긋나는 순간이 이 프로젝트의 결정적 장면**이다.
   *
   * ## 계산 규칙 — 워커와 동일하게 맞춘다
   *
   * 1. **`occurredAt` 순서로 정렬**한다 (도착 순서가 아니라)
   * 2. 적립은 더하고, 사용은 **잔액이 충분할 때만** 뺀다 (부족하면 거절)
   *
   * ### 왜 도착 순서가 아니라 `occurredAt` 인가
   *
   * `occurredAt` 은 `ledger-api` 가 발행 시점에 찍은 값이므로
   * **"원래 일어났어야 할 순서"** 를 나타낸다.
   *
   * 4단계에서 파티션 키를 흩뜨리면 SSE 도착 순서도 뒤섞이는데,
   * 도착 순서로 계산하면 기대 잔액까지 같이 틀려져 **비교 대상이 되지 못한다.**
   *
   * `occurredAt` 으로 정렬하면 파티션 배치와 무관하게 항상 올바른 값이 나오므로,
   * 워커가 순서를 어겼을 때 그 차이가 드러난다.
   */
  const expectedBalances = computed(() => {
    const ordered = [...events.value].sort((a, b) =>
      a.occurredAt < b.occurredAt ? -1 : a.occurredAt > b.occurredAt ? 1 : 0,
    )

    const balances = new Map<string, number>()
    const rejected = new Map<string, number>()

    for (const e of ordered) {
      const current = balances.get(e.userId) ?? 0
      if (e.eventType === 'POINT_EARNED') {
        balances.set(e.userId, current + e.amount)
      } else if (current >= e.amount) {
        balances.set(e.userId, current - e.amount)
      } else {
        // 잔액 부족 — 워커와 동일하게 거절한다.
        balances.set(e.userId, current)
        rejected.set(e.userId, (rejected.get(e.userId) ?? 0) + 1)
      }
    }

    return { balances, rejected }
  })

  /** 파티션별 수신 건수. 해시 편중을 화면에서 확인하는 용도. */
  const countByPartition = computed(() => {
    const map = new Map<number, number>()
    for (const e of events.value) {
      map.set(e.partition, (map.get(e.partition) ?? 0) + 1)
    }
    return map
  })

  /** 특정 사용자가 사용한 파티션 목록. `USER_ID` 모드면 1개여야 한다. */
  function partitionsOf(userId: string): number[] {
    const set = new Set<number>()
    for (const e of events.value) {
      if (e.userId === userId) set.add(e.partition)
    }
    return [...set].sort()
  }

  return { events, push, clear, expectedBalances, countByPartition, partitionsOf }
})
