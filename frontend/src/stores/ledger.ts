import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { LedgerEvent } from '../api/types'

/** 피드에 표시할 최대 이벤트 수. 이보다 오래된 것은 **피드에서만** 버린다. */
const MAX_FEED = 300

export const useLedgerStore = defineStore('ledger', () => {
  /** 피드 표시용. 최신이 앞. 최대 [MAX_FEED] 개, [clear] 로 비울 수 있다. */
  const events = ref<LedgerEvent[]>([])

  /**
   * 기대 잔액 계산용 — 화면을 켠 뒤 받은 **모든** 거래.
   *
   * 예전에는 피드 목록(최근 300개)으로 기대 잔액을 계산해서, 거래가 300건을 넘거나
   * 피드를 [지우기] 하면 **기대 잔액 자체가 틀어졌다** (KS-42). 그래서 둘을 나눴다.
   */
  const history = ref<LedgerEvent[]>([])

  function push(event: LedgerEvent) {
    history.value.push(event)
    events.value.unshift(event)
    if (events.value.length > MAX_FEED) {
      events.value.length = MAX_FEED
    }
  }

  /** 피드 표시만 비운다. 기대 잔액 계산에는 영향이 없다. */
  function clear() {
    events.value = []
  }

  /**
   * **기대 잔액** — 프론트가 SSE 로 받은 이벤트를 스스로 계산한 값.
   *
   * `balance-worker` 가 계산한 실제 잔액과 나란히 표시한다.
   * 3단계에서는 둘이 일치하지만, **4단계에서 어긋나는 순간이 이 프로젝트의 가장 중요한 장면**이다.
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
    const ordered = [...history.value].sort((a, b) =>
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

  /**
   * 화면을 켜기 **전**에 이미 기록된 거래가 있는 파티션.
   *
   * 화면이 파티션에서 처음 받은 거래의 offset 이 0 보다 크면, 그 앞의 거래는 받지 못했다는 뜻이다.
   * 그 파티션의 사용자는 기대 잔액이 부족하게 계산된다.
   *
   * 예전에는 "워커가 처리한 건수 > 화면이 받은 건수"로 판단해서, replay 로 **같은 거래를 두 번 처리**하면
   * 놓친 거래가 없는데도 경고가 떴다 (KS-42). replay 는 Kafka 에 새 기록을 만들지 않으므로 offset 기준은 흔들리지 않는다.
   */
  const missedPartitions = computed(() => {
    const first = new Map<number, number>()
    for (const e of history.value) {
      const seen = first.get(e.partition)
      if (seen === undefined || e.offset < seen) first.set(e.partition, e.offset)
    }
    return [...first.entries()].filter(([, offset]) => offset > 0).map(([p]) => p).sort((a, b) => a - b)
  })

  return { events, push, clear, expectedBalances, countByPartition, partitionsOf, missedPartitions }
})
