<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

interface WorkerRow {
  workerId: string
  partitions: number[]
  generation: number
  lastHeartbeat: string
  secondsSinceHeartbeat: number
  /** 하트비트가 최근인가. `kill -9`로 죽으면 배정 기록은 남지만 false가 된다. */
  alive: boolean
  /** 정상 종료했는가. 파티션을 반납하고 스스로 남긴 기록이다. */
  stopped: boolean
}

interface WorkersResponse {
  workers: WorkerRow[]
  aliveCount: number
  assignedPartitions: number[]
}

const workers = ref<WorkerRow[]>([])
const assigned = ref<number[]>([])
const online = ref(false)
let timer: number | null = null

async function poll() {
  try {
    const res = await fetch('/api/workers')
    if (!res.ok) throw new Error()
    const data: WorkersResponse = await res.json()
    workers.value = data.workers
    assigned.value = data.assignedPartitions
    online.value = true
  } catch {
    online.value = false
  }
}

onMounted(() => {
  void poll()
  timer = window.setInterval(poll, 1000)
})
onUnmounted(() => {
  if (timer !== null) clearInterval(timer)
})

const ALL_PARTITIONS = [0, 1, 2]

const alive = computed(() => workers.value.filter((w) => w.alive))
/** 하트비트가 끊긴 워커 — `kill -9` 처럼 알릴 틈 없이 죽은 경우. */
const dead = computed(() => workers.value.filter((w) => !w.alive && !w.stopped))
/** 스스로 파티션을 반납하고 종료한 워커. */
const stopped = computed(() => workers.value.filter((w) => w.stopped))

/** 아무도 담당하지 않는 파티션. 리밸런싱이 진행 중이면 잠시 나타난다. */
const orphaned = computed(() => ALL_PARTITIONS.filter((p) => !assigned.value.includes(p)))

/** 파티션을 못 받은 워커. 워커 수 > 파티션 수일 때 나타난다. */
const idle = computed(() => alive.value.filter((w) => w.partitions.length === 0))
</script>

<template>
  <section class="panel">
    <header>
      <h2>④ 워커 · 파티션 배정</h2>
      <span class="status" :class="{ on: online }">
        {{ online ? `살아있는 워커 ${alive.length}` : '연결 중…' }}
      </span>
    </header>

    <div v-if="workers.length" class="workers">
      <div v-for="w in workers" :key="w.workerId" class="worker" :class="{ dead: !w.alive }">
        <div class="wid">
          {{ w.workerId }}
          <span v-if="w.stopped" class="tag stopped">정상 종료</span>
          <span v-else-if="!w.alive" class="tag">죽음 ({{ w.secondsSinceHeartbeat }}s)</span>
          <span v-else-if="!w.partitions.length" class="tag idle">놀고 있음</span>
        </div>
        <div class="parts">
          <span
            v-for="p in w.partitions"
            :key="p"
            class="badge"
            :data-p="p"
          >p{{ p }}</span>
          <span v-if="!w.partitions.length" class="none">—</span>
        </div>
        <div class="gen">gen {{ w.generation }}</div>
      </div>
    </div>
    <p v-else class="empty">워커를 기동하면 여기에 나타납니다.</p>

    <p v-if="idle.length" class="note warn">
      ⚠️ <strong>{{ idle.length }}개 워커가 놀고 있습니다.</strong>
      파티션은 {{ ALL_PARTITIONS.length }}개뿐이라
      <strong>파티션 수가 병렬 처리의 상한</strong>입니다.
      컨슈머를 늘려도 파티션보다 많아지면 소용이 없습니다.
    </p>

    <p v-if="orphaned.length" class="note warn">
      ⏳ 담당자 없는 파티션: {{ orphaned.map((p) => `p${p}`).join(', ') }}
      — 리밸런싱 중이거나 워커가 부족합니다. 그동안 이 파티션은 처리되지 않습니다.
    </p>

    <p v-if="stopped.length" class="note">
      정상 종료한 워커는 <code>onPartitionsRevoked</code> 에서 파티션을 반납하고
      스스로 종료를 기록합니다. 그래서 재배정이 빠릅니다.
    </p>

    <p v-if="dead.length" class="note">
      죽은 워커의 배정 기록이 남아 있습니다.
      <code>kill -9</code> 로 종료하면 <code>onPartitionsRevoked</code> 가 불리지 않아
      DB를 정리할 기회가 없기 때문입니다.
      <strong>"죽었다"를 아는 유일한 방법은 "한동안 소식이 없다"뿐입니다</strong>
      — Kafka도 <code>session.timeout.ms</code> 로 같은 판단을 합니다.
    </p>
  </section>
</template>

<style scoped>
header { display: flex; justify-content: space-between; align-items: baseline; }
.status { font-size: 12px; color: #e88; }
.status.on { color: #6c6; }

.workers { display: flex; flex-direction: column; gap: 6px; }
.worker {
  display: grid;
  grid-template-columns: 150px 1fr 56px;
  gap: 8px;
  align-items: center;
  padding: 7px 10px;
  border-radius: 6px;
  background: var(--surface-2);
  font-size: 13px;
}
.worker.dead { opacity: 0.45; }
.wid { font-weight: 600; font-family: ui-monospace, monospace; font-size: 12px; }
.tag {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 700;
  background: #4a2020;
  color: #f99;
}
.tag.idle { background: #4a3f20; color: #e8c86a; }
.tag.stopped { background: #2a3a4a; color: #9cc4e8; }
.parts { display: flex; gap: 4px; flex-wrap: wrap; }
.none { color: var(--muted); }
.gen { text-align: right; font-size: 11px; color: var(--muted); }

.empty { color: var(--muted); font-size: 13px; padding: 12px 0; }
.note {
  margin: 10px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--muted);
  border-top: 1px solid var(--border);
  padding-top: 8px;
}
.note.warn { color: #e8c86a; border-top: none; }
</style>
