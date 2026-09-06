<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useBalanceStore } from '../stores/balance'
import { useLedgerStore } from '../stores/ledger'

const balance = useBalanceStore()
const ledger = useLedgerStore()

onMounted(() => balance.start(1000))

interface Row {
  userId: string
  worker: number | null
  expected: number | null
  diff: number | null
  workerRejected: number
  expectedRejected: number
}

/**
 * 워커 잔액과 기대 잔액을 나란히 놓는다.
 *
 * **이 대조가 이 화면의 전부다.**
 * 워커가 계산한 값만 보면 그것이 틀렸는지 알 방법이 없다.
 * 프론트가 독립적으로 계산한 값과 비교해야 4단계에서 어긋나는 것을 알아챌 수 있다.
 */
const rows = computed<Row[]>(() => {
  const { balances: exp, rejected: expRej } = ledger.expectedBalances
  const workerMap = new Map(balance.rows.map((r) => [r.userId, r]))
  const userIds = new Set<string>([...workerMap.keys(), ...exp.keys()])

  return [...userIds].sort().map((userId) => {
    const w = workerMap.get(userId)
    const e = exp.get(userId)
    return {
      userId,
      worker: w?.balance ?? null,
      expected: e ?? null,
      diff: w != null && e != null ? w.balance - e : null,
      workerRejected: w?.rejectedEvents ?? 0,
      expectedRejected: expRej.get(userId) ?? 0,
    }
  })
})

const mismatches = computed(() => rows.value.filter((r) => r.diff !== null && r.diff !== 0).length)

/**
 * 기대 잔액은 **SSE 로 받은 이벤트만** 가지고 계산한다.
 * 화면을 켜기 전에 발행된 거래는 프론트가 모르므로 비교가 성립하지 않는다.
 */
const partialData = computed(() =>
  balance.totalApplied + balance.totalRejected > ledger.events.length,
)

function fmt(n: number | null) {
  return n === null ? '—' : n.toLocaleString('ko-KR')
}

const busy = ref(false)
const lastAction = ref<string | null>(null)

/**
 * 재생 두 종류.
 *
 * 차이는 **잔액 테이블을 비우느냐** 한 줄뿐인데,
 * 그것이 "정확히 복원"과 "두 배가 됨"을 가른다.
 */
async function runReplay(mode: 'rebuild' | 'replay') {
  busy.value = true
  lastAction.value = null
  try {
    const res = await fetch(`/api/balance/${mode}`, { method: 'POST' })
    const data = await res.json()
    lastAction.value = `${data.mode} → p${data.partitions.join(', p')} 되감음`
  } catch (e) {
    lastAction.value = `실패: ${e}`
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="panel">
    <header>
      <h2>③ 사용자별 잔액</h2>
      <span class="status" :class="{ on: balance.online }">
        {{ balance.online ? 'worker 연결됨' : (balance.error ?? '연결 중…') }}
      </span>
    </header>

    <div class="summary">
      <span>적용 <strong>{{ balance.totalApplied }}</strong></span>
      <span>거절 <strong>{{ balance.totalRejected }}</strong></span>
      <span
        class="verdict"
        :class="mismatches ? 'bad' : 'good'"
      >{{ mismatches ? `불일치 ${mismatches}명 😱` : '일치 ✓' }}</span>
    </div>

    <table v-if="rows.length">
      <thead>
        <tr>
          <th>사용자</th>
          <th>워커 잔액</th>
          <th>기대 잔액</th>
          <th>차이</th>
          <th>거절</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.userId" :class="{ mismatch: r.diff !== null && r.diff !== 0 }">
          <td class="user">{{ r.userId }}</td>
          <td class="num">{{ fmt(r.worker) }}</td>
          <td class="num">{{ fmt(r.expected) }}</td>
          <td class="num diff">
            {{ r.diff === null ? '—' : r.diff === 0 ? '0' : (r.diff > 0 ? '+' : '') + fmt(r.diff) }}
          </td>
          <td class="num small">{{ r.workerRejected }} / {{ r.expectedRejected }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">거래를 발생시키면 잔액이 여기에 나타납니다.</p>

    <div class="replay">
      <button :disabled="busy" @click="runReplay('rebuild')">
        rebuild — 잔액 비우고 재생
      </button>
      <button class="danger" :disabled="busy" @click="runReplay('replay')">
        replay — 잔액 유지한 채 재생 😱
      </button>
    </div>
    <p v-if="lastAction" class="action">{{ lastAction }}</p>

    <p v-if="partialData" class="note warn">
      ⚠️ 화면을 켜기 <strong>전</strong>에 발행된 거래가 있어 기대 잔액이 부족하게 계산됩니다.
      정확히 비교하려면 워커의 잔액을 재구축하거나, 이 화면을 켠 뒤 발생한 거래만으로 확인하세요.
    </p>
    <p class="note">
      <strong>기대 잔액</strong>은 프론트가 SSE 로 받은 이벤트를
      <code>occurredAt</code> 순서로 정렬해 스스로 계산한 값입니다.
      워커가 순서를 지켜 처리하면 두 값이 일치합니다.
      <strong>4단계에서 파티션 키를 흩뜨리면 어긋나기 시작합니다.</strong>
    </p>
  </section>
</template>

<style scoped>
header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.status { font-size: 12px; color: #e88; }
.status.on { color: #6c6; }

.summary {
  display: flex;
  gap: 14px;
  align-items: center;
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 10px;
}
.summary strong { color: var(--text); }
.verdict {
  margin-left: auto;
  padding: 2px 10px;
  border-radius: 4px;
  font-weight: 700;
}
.verdict.good { background: #1a3d2a; color: #7d7; }
.verdict.bad { background: #4a2020; color: #f99; }

table { width: 100%; border-collapse: collapse; font-size: 13px; }
th {
  text-align: right;
  font-weight: 500;
  font-size: 11px;
  color: var(--muted);
  padding: 4px;
  border-bottom: 1px solid var(--border);
}
th:first-child { text-align: left; }
td { padding: 5px 4px; border-bottom: 1px solid var(--border); }
.user { font-weight: 600; }
.num { text-align: right; font-variant-numeric: tabular-nums; }
.small { font-size: 11px; color: var(--muted); }
tr.mismatch { background: #3a1c1c; }
tr.mismatch .diff { color: #f99; font-weight: 700; }

.empty { color: var(--muted); font-size: 13px; padding: 12px 0; }

.replay { display: flex; gap: 6px; margin-top: 12px; }
.replay button { flex: 1; font-size: 12px; padding: 7px; }
.replay .danger { background: #4a2020; color: #f99; }
.action { margin: 6px 0 0; font-size: 11px; color: var(--muted); font-family: ui-monospace, monospace; }
.note {
  margin: 10px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--muted);
  border-top: 1px solid var(--border);
  padding-top: 8px;
}
.note.warn { color: #e8a0a0; border-top: none; }
</style>
