# KS-15: 프론트 뼈대 + 거래 폼 + 실시간 원장 피드

| | |
|---|---|
| **타입** | FEAT |
| **상태** | TODO |
| **단계** | 2단계 |
| **브랜치** | `feature/KS-15-frontend-ledger-feed` |
| **PR** | - |

## 배경

프론트는 **Kafka의 흐름을 눈으로 보는 창**이다. 화면은 최소로 유지하고,
프론트 작업이 Kafka 학습을 압도하지 않게 한다.

## 목표

거래를 발생시키고, 그것이 어느 파티션으로 갔는지 화면에서 확인한다.

## 작업 내용

- [ ] Vite + Vue 3 + TypeScript 프로젝트 생성
- [ ] Pinia, Chart.js 설치
- [ ] `vite.config.ts` — 백엔드 3개 프록시 (CORS 회피)
- [ ] `TxForm.vue` — userId / 적립·사용 / 금액. **userId 프리셋 버튼**
- [ ] `useSse.ts` 컴포저블 — `EventSource` 연결/해제
- [ ] `stores/ledger.ts` — 수신 이벤트 목록
- [ ] `LedgerFeed.vue` — 시간순 목록 + **partition/offset 뱃지** (파티션별 색상)
- [ ] `App.vue` 4분할 레이아웃 (폼 / 피드 / 잔액 / 파티션 보드 — 뒤 둘은 자리만)

## 상세 설계

### userId 프리셋이 중요한 이유

**같은 userId로 연속 거래를 빠르게 만드는 것**이 이 프로젝트의 핵심 조작이다.
매번 입력하게 하면 4단계 실험이 번거로워진다. `user-1` ~ `user-5` 버튼을 둔다.

### partition 뱃지

파티션 번호마다 색을 다르게 준다.
같은 userId로 거래하면 **같은 색만 계속 쌓이는 것**이 한눈에 보인다.
4단계에서 `RANDOM` 모드로 바꾸면 **색이 섞이기 시작한다.**

### SSE 생명주기

`onMounted` 연결, `onUnmounted` `close()`. **해제를 빠뜨리면 연결이 누적된다.**

`EventSource`는 끊기면 브라우저가 자동 재연결하지만 **재연결 중 놓친 이벤트는 복구되지 않는다.**
Kafka의 offset은 **컨슈머 서비스가** 어디까지 읽었는지를 기억할 뿐, 브라우저가 어디까지 받았는지는 모른다.

### CORS

Vite dev 프록시로 처리한다. 백엔드에 CORS 설정을 흩뿌리지 않는다.

| 프론트 경로 | 대상 |
|---|---|
| `/api/tx`, `/api/stream`, `/api/config` | `localhost:8081` |
| `/api/balance`, `/api/workers` | `localhost:8082` |
| `/api/fraud` | `localhost:8083` |

## 완료 조건 (DoD)

- [ ] `npm run dev` → `localhost:5173` 접속
- [ ] 폼 제출 → 202 응답 (CORS 에러 없음)
- [ ] 거래가 피드에 **즉시** 나타남
- [ ] **같은 userId로 5건 → 뱃지 색(partition)이 전부 동일**
- [ ] 다른 userId로 거래 시 다른 색이 섞임
- [ ] 페이지 이동/새로고침 시 SSE 연결이 정상 해제됨

## 배운 것

_(작업 후 작성)_

## 참고

- [02. 설계서 — 프론트엔드 구조](../planning/02-architecture.md)
