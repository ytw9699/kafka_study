<script setup lang="ts">
import { computed } from 'vue'
import { useSse } from '../composables/useSse'
import { useLedgerStore } from '../stores/ledger'
import type { LedgerEvent } from '../api/types'

const store = useLedgerStore()

const { connected, error } = useSse<LedgerEvent>('/api/stream', 'ledger', (e) => store.push(e))

const partitionSummary = computed(() =>
  [...store.countByPartition.entries()].sort((a, b) => a[0] - b[0]),
)

function fmt(n: number) {
  return n.toLocaleString('ko-KR')
}

function time(iso: string) {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour12: false })
}
</script>

<template>
  <section class="panel">
    <header>
      <h2>② 실시간 원장 피드</h2>
      <span class="status" :class="{ on: connected }">
        {{ connected ? 'SSE 연결됨' : (error ?? '연결 중…') }}
      </span>
    </header>

    <!--
      파티션별 수신 건수. 해시 편중이 여기서 눈에 보인다.
      사용자 수가 적으면 특정 파티션에 몰린다.
    -->
    <div class="partitions">
      <span
        v-for="[p, n] in partitionSummary"
        :key="p"
        class="badge"
        :data-p="p"
      >p{{ p }} · {{ n }}</span>
      <span v-if="!partitionSummary.length" class="muted">아직 수신한 이벤트가 없습니다</span>
      <button v-if="store.events.length" class="clear" @click="store.clear()">지우기</button>
    </div>

    <div class="feed">
      <div v-for="e in store.events" :key="e.eventId" class="row">
        <!--
          partition 뱃지가 이 화면의 핵심이다.
          같은 userId 로 반복 거래하면 같은 색만 계속 쌓이는 것이 한눈에 보이고,
          RANDOM 모드로 바꾸면 색이 섞이기 시작한다.
        -->
        <span class="badge" :data-p="e.partition">p{{ e.partition }}</span>
        <span class="offset">#{{ e.offset }}</span>
        <span class="user">{{ e.userId }}</span>
        <span class="amount" :class="e.eventType === 'POINT_EARNED' ? 'earn' : 'spend'">
          {{ e.eventType === 'POINT_EARNED' ? '＋' : '－' }}{{ fmt(e.amount) }}
        </span>
        <span class="reason">{{ e.reason }}</span>
        <span class="time">{{ time(e.occurredAt) }}</span>
      </div>
      <p v-if="!store.events.length" class="empty">
        거래를 발생시키면 여기에 실시간으로 나타납니다.
      </p>
    </div>

    <p class="note">
      SSE 연결 <strong>이전</strong>에 발생한 이벤트는 보이지 않습니다.
      재연결 중 놓친 것도 복구되지 않습니다 — Kafka 의 offset 은
      <em>컨슈머 서비스가</em> 어디까지 읽었는지를 기억할 뿐,
      브라우저가 어디까지 받았는지는 모릅니다.
    </p>
  </section>
</template>

<style scoped>
header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.status {
  font-size: 12px;
  color: #e88;
}
.status.on { color: #6c6; }

.partitions {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 8px;
  min-height: 24px;
}
.partitions .clear {
  margin-left: auto;
  font-size: 11px;
  padding: 2px 8px;
}
.muted { color: var(--muted); font-size: 12px; }

.feed {
  max-height: 420px;
  overflow-y: auto;
  font-size: 13px;
}
.row {
  display: grid;
  grid-template-columns: 34px 52px 72px 90px 1fr 64px;
  gap: 8px;
  align-items: center;
  padding: 5px 4px;
  border-bottom: 1px solid var(--border);
}
.offset { color: var(--muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.user { font-weight: 600; }
.amount { text-align: right; font-variant-numeric: tabular-nums; }
.amount.earn { color: #6c6; }
.amount.spend { color: #e88; }
.reason { color: var(--muted); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.time { color: var(--muted); font-size: 11px; text-align: right; }
.empty { color: var(--muted); font-size: 13px; padding: 16px 0; }

.note {
  margin: 10px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--muted);
  border-top: 1px solid var(--border);
  padding-top: 8px;
}
</style>
