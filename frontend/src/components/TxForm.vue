<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { burst, createTx, getKeyMode, setKeyMode } from '../api/ledger'
import type { PartitionKeyMode, PublishResult } from '../api/types'

// 같은 userId 로 연속 거래를 빠르게 만드는 것이 이 프로젝트의 핵심 조작이다.
// 매번 입력하게 하면 4단계 실험이 번거로우므로 프리셋을 둔다.
const PRESETS = ['user-1', 'user-2', 'user-3', 'user-4', 'user-5']

const userId = ref(PRESETS[0])
const amount = ref(5000)
const reason = ref('')
const last = ref<PublishResult | null>(null)
const error = ref<string | null>(null)
const keyMode = ref<PartitionKeyMode>('USER_ID')
const busy = ref(false)

// 파티션 키 모드를 주기적으로 확인한다.
//
// API 로 직접 바꿀 수도 있으므로(실험 스크립트 등) 화면이 stale 해지면 안 된다.
// 이 값이 틀리면 "지금 정상 모드인가 실험 모드인가"를 오판하게 되어
// 4단계 실험의 해석 자체가 어긋난다.
let modeTimer: number | null = null

async function syncKeyMode() {
  try {
    keyMode.value = await getKeyMode()
    if (error.value?.includes('ledger-api')) error.value = null
  } catch {
    error.value = 'ledger-api(:8081)에 연결할 수 없습니다'
  }
}

onMounted(() => {
  void syncKeyMode()
  modeTimer = window.setInterval(syncKeyMode, 2000)
})

onUnmounted(() => {
  if (modeTimer !== null) clearInterval(modeTimer)
})

async function submit(type: 'EARN' | 'SPEND') {
  error.value = null
  busy.value = true
  try {
    last.value = await createTx({
      userId: userId.value,
      type,
      amount: amount.value,
      reason: reason.value,
    })
  } catch (e) {
    error.value = String(e)
  } finally {
    busy.value = false
  }
}

async function toggleKeyMode() {
  const next: PartitionKeyMode = keyMode.value === 'USER_ID' ? 'RANDOM' : 'USER_ID'
  const res = await setKeyMode(next)
  keyMode.value = res.mode
}

async function runBurst() {
  busy.value = true
  try {
    const r = await burst({ users: 5, txPerUser: 10 })
    error.value = null
    last.value = null
    alert(`${r.published}건 발행\n파티션별: ${JSON.stringify(r.byPartition)}`)
  } catch (e) {
    error.value = String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="panel">
    <h2>① 거래 발생</h2>

    <div class="presets">
      <button
        v-for="p in PRESETS"
        :key="p"
        :class="{ active: userId === p }"
        @click="userId = p"
      >
        {{ p }}
      </button>
    </div>

    <label>
      사용자
      <input v-model="userId" />
    </label>

    <label>
      금액
      <input v-model.number="amount" type="number" step="1000" />
    </label>

    <label>
      사유
      <input v-model="reason" placeholder="(선택)" />
    </label>

    <div class="actions">
      <button class="earn" :disabled="busy" @click="submit('EARN')">＋ 적립</button>
      <button class="spend" :disabled="busy" @click="submit('SPEND')">－ 사용</button>
    </div>

    <hr />

    <!--
      4단계 실험 A 의 조작 장치.
      RANDOM 으로 바꾸면 같은 사용자의 거래가 여러 파티션에 흩어져
      병렬 워커가 순서를 뒤바꿔 처리하고, 잔액이 틀어진다.
    -->
    <div class="keymode" :class="keyMode.toLowerCase()">
      <div>
        <strong>파티션 키</strong>
        <code>{{ keyMode }}</code>
      </div>
      <button @click="toggleKeyMode">
        {{ keyMode === 'USER_ID' ? 'RANDOM 으로 (실험)' : 'USER_ID 로 복구' }}
      </button>
    </div>
    <p v-if="keyMode === 'RANDOM'" class="warn">
      ⚠️ 같은 사용자의 거래가 여러 파티션에 흩어집니다.
      4단계에서 워커를 여러 개 띄우면 <strong>잔액이 틀어집니다.</strong>
    </p>

    <button class="burst" :disabled="busy" @click="runBurst">
      연속 거래 생성 (5명 × 10건)
    </button>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="last" class="result">
      발행됨 →
      <span class="badge" :data-p="last.partition">p{{ last.partition }}</span>
      <span class="offset">offset {{ last.offset }}</span>
      <div class="key">key: <code>{{ last.partitionKey }}</code></div>
    </div>
  </section>
</template>

<style scoped>
.presets {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.presets button {
  padding: 4px 10px;
  font-size: 12px;
  background: var(--surface-2);
}
.presets button.active {
  background: var(--accent);
  color: #fff;
}
label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--muted);
}
label input {
  display: block;
  width: 100%;
  margin-top: 4px;
}
.actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.actions button {
  flex: 1;
  padding: 10px;
  font-weight: 600;
}
.earn { background: #1a7f4b; color: #fff; }
.spend { background: #a33; color: #fff; }

.keymode {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--surface-2);
}
.keymode.random {
  background: #4a2020;
  outline: 1px solid #a33;
}
.keymode code { font-size: 12px; }
.warn {
  font-size: 12px;
  color: #e8a0a0;
  margin: 8px 0 0;
  line-height: 1.5;
}
.burst {
  width: 100%;
  margin-top: 12px;
  padding: 8px;
  font-size: 13px;
}
.error {
  color: #e88;
  font-size: 13px;
}
.result {
  margin-top: 12px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--surface-2);
  font-size: 13px;
}
.result .offset { color: var(--muted); margin-left: 6px; }
.result .key { margin-top: 4px; font-size: 11px; color: var(--muted); }
</style>
