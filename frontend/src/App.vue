<script setup lang="ts">
import TxForm from './components/TxForm.vue'
import LedgerFeed from './components/LedgerFeed.vue'
import BalancePanel from './components/BalancePanel.vue'
import PartitionBoard from './components/PartitionBoard.vue'
</script>

<template>
  <div class="app">
    <header class="top">
      <h1>Kafka Study — 포인트 카드로 배우는 Kafka</h1>
      <p>
        같은 사용자의 거래 순서는 지키면서 여러 사용자를 동시에 처리하고,
        워커가 죽어도 자동으로 이어받으며, 과거 거래까지 다시 읽어야 해서 Kafka를 쓴다.
      </p>
    </header>

    <main class="grid">
      <TxForm />
      <LedgerFeed />
      <BalancePanel />
      <PartitionBoard />
    </main>
  </div>
</template>

<style scoped>
.top { margin-bottom: 20px; }
.top h1 { font-size: 20px; margin: 0 0 6px; }
.top p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--muted);
  max-width: 70ch;
}
.grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
}
.placeholder p { color: var(--muted); font-size: 13px; }
</style>
