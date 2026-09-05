import { defineStore } from 'pinia'
import { onUnmounted, ref } from 'vue'

export interface BalanceRow {
  userId: string
  balance: number
  appliedEvents: number
  /** 잔액 부족으로 거절된 사용 건수 */
  rejectedEvents: number
  updatedAt: string
}

interface BalancesResponse {
  balances: BalanceRow[]
  totalApplied: number
  totalRejected: number
}

/**
 * `balance-worker` 가 계산한 **실제 잔액**.
 *
 * SSE 가 아니라 폴링이다 — 워커는 4단계에서 여러 인스턴스가 되므로
 * SSE 를 붙이면 브라우저가 하나에만 연결되어 일부만 보게 된다.
 * 잔액은 공유 DB 에 있으므로 **어느 인스턴스에 물어도 같다.**
 */
export const useBalanceStore = defineStore('balance', () => {
  const rows = ref<BalanceRow[]>([])
  const totalApplied = ref(0)
  const totalRejected = ref(0)
  const online = ref(false)
  const error = ref<string | null>(null)

  let timer: number | null = null

  async function fetchOnce() {
    try {
      const res = await fetch('/api/balance')
      if (!res.ok) throw new Error(String(res.status))
      const data: BalancesResponse = await res.json()
      rows.value = data.balances
      totalApplied.value = data.totalApplied
      totalRejected.value = data.totalRejected
      online.value = true
      error.value = null
    } catch {
      online.value = false
      error.value = 'balance-worker(:8082) 연결 안 됨'
    }
  }

  function start(intervalMs = 1000) {
    if (timer !== null) return
    void fetchOnce()
    timer = window.setInterval(fetchOnce, intervalMs)
  }

  function stop() {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  onUnmounted(stop)

  return { rows, totalApplied, totalRejected, online, error, start, stop, fetchOnce }
})
