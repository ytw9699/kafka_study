# frontend — 실험을 눈으로 보는 화면

[← 프로젝트 README](../README.md)

Vue 3 + TypeScript + Vite 로 만든 화면입니다. **Kafka 가 하는 일을 숫자와 색으로 보여주는 창** 역할만 합니다.

## 실행

```bash
npm install && npm run dev
```

<http://localhost:5173> 에서 열립니다. 백엔드(`ledger-api` :8081, `balance-worker` :8082)가 먼저 떠 있어야 합니다.
실행 순서 전체는 [프로젝트 README 5번](../README.md#5-직접-실행해-보기-약-10분)을 보세요.

## 화면 구성

| 영역 | 파일 | 보여주는 것 |
|---|---|---|
| ① 거래 발생 | `components/TxForm.vue` | 적립 · 사용 버튼, **파티션 키 전환**(USER_ID ↔ RANDOM), 연속 거래 생성 |
| ② 실시간 원장 피드 | `components/LedgerFeed.vue` | 들어온 거래가 **어느 파티션(p0 · p1 · p2)** 에 들어갔는지 색으로 표시 |
| ③ 사용자별 잔액 | `components/BalancePanel.vue` | **워커 잔액 vs 기대 잔액**, 일치 · 불일치 판정, rebuild · replay 버튼, 실패 처리 모드와 **"보류"** 된 사용자 |
| ④ 워커 · 파티션 배정 | `components/PartitionBoard.vue` | 워커별 담당 파티션, "놀고 있음" · "정상 종료" · "죽음" 표시 |

## 핵심 장치 — 기대 잔액

`stores/ledger.ts` 는 SSE(`composables/useSse.ts`)로 받은 거래를 **발생 시각 순으로 정렬해 직접 계산**합니다.
`stores/balance.ts` 가 워커에게 물어 온 잔액과 비교해, 둘이 다르면 순서가 깨졌거나 거래가 빠졌다는 뜻입니다.

도착 순서가 아니라 발생 시각 순으로 계산하는 이유: 도착 순서로 계산하면 순서가 깨질 때 **기대 잔액도 같이 틀려져** 비교가 의미 없어지기 때문입니다.

> 브라우저를 새로 고치면 기대 잔액은 비워집니다. 새로 고침 이전 거래는 SSE 로 다시 받지 못하기 때문입니다.

## 백엔드 연결

포트가 셋으로 나뉘어 있어 `vite.config.ts` 의 개발 서버 프록시로 경로별로 나눠 보냅니다.

| 경로 | 보내는 곳 |
|---|---|
| `/api/tx`, `/api/config`, `/api/stream` | `ledger-api` :8081 |
| `/api/balance`, `/api/workers` | `balance-worker` :8082 |
