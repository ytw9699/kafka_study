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
   * **기대 잔액** — 프론트가 SSE 로 받은 이벤트를 스스로 합산한 값.
   *
   * 3단계부터 `balance-worker` 가 계산한 실제 잔액과 나란히 표시한다.
   * 지금은 둘이 같지만, **4단계에서 어긋나는 순간이 이 프로젝트의 결정적 장면**이다.
   *
   * 주의: 여기서는 도착 순서대로 단순 합산한다.
   * 워커와 달리 "잔액 부족 시 거절"을 적용하지 않으므로 정확히는 **총 증감**에 가깝다.
   * 4단계에서 워커가 순서를 뒤바꿔 거절 판정을 잘못하면 이 값과 벌어진다.
   */
  const expectedBalances = computed(() => {
    const map = new Map<string, number>()
    // events 는 최신이 앞이므로 뒤에서부터(오래된 것부터) 적용한다.
    for (let i = events.value.length - 1; i >= 0; i--) {
      const e = events.value[i]
      const delta = e.eventType === 'POINT_EARNED' ? e.amount : -e.amount
      map.set(e.userId, (map.get(e.userId) ?? 0) + delta)
    }
    return map
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
