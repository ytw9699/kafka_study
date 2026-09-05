# KS-8: Vue 프로젝트 뼈대 + 주문 생성 폼

| | |
|---|---|
| **타입** | FEAT |
| **상태** | TODO |
| **단계** | 2단계 — 실시간 Consumer + 프론트 |
| **브랜치** | `feature/KS-8-frontend-scaffold` |
| **PR** | - |

## 배경

프론트는 **Kafka의 흐름을 눈으로 보는 창**이다. 화면은 최소로 유지하고,
프론트 작업이 Kafka 학습을 압도하지 않게 한다.

## 목표

Vue 프로젝트를 세우고 주문을 생성할 수 있게 한다.

## 작업 내용

- [ ] Vite + Vue 3 + TypeScript 프로젝트 생성
- [ ] Pinia, Chart.js 설치
- [ ] `vite.config.ts` — 백엔드 3개로 프록시 설정
- [ ] `OrderForm.vue` — userId / 상품명 / 수량 / 단가 입력
- [ ] `api/order.ts` — 주문 생성 호출
- [ ] `App.vue` 3분할 레이아웃 (폼 / 피드 / 통계)

## 상세 설계

### CORS는 프록시로 처리

백엔드 3개 포트를 상대하므로 Vite dev 서버 프록시로 처리한다.
**백엔드에 CORS 설정을 흩뿌리지 않는다.**

| 프론트 경로 | 프록시 대상 |
|---|---|
| `/api/orders` | `localhost:8081` |
| `/api/feed` | `localhost:8082` |
| `/api/stats` | `localhost:8083` |

### OrderForm의 학습상 역할

**같은 `userId`로 반복 주문하기 쉬워야 한다.** 파티션 고정을 확인하는 도구이므로,
userId를 매번 새로 입력하지 않아도 되게 값을 유지하고, 프리셋 버튼(`user-1`, `user-2`, `user-3`)을 둔다.

## 완료 조건 (DoD)

- [ ] `npm run dev`로 기동, `localhost:5173` 접속
- [ ] 폼 제출 → 201 응답 수신 (CORS 에러 없음)
- [ ] 응답의 `partition` / `offset`이 화면에 표시됨
- [ ] userId 프리셋으로 같은 사용자 주문을 빠르게 반복 가능

## 배운 것

_(작업 후 작성)_

## 참고

- [02. 설계서 — 프론트엔드 구조](../planning/02-architecture.md)
