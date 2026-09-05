# KS-16: balance-worker — 잔액 계산 컨슈머

| | |
|---|---|
| **타입** | FEAT |
| **상태** | TODO |
| **단계** | 3단계 |
| **브랜치** | `feature/KS-16-balance-worker` |
| **PR** | - |

## 배경

지금까지는 이벤트를 **발행하고 보기만** 했다. 이제 그것을 **읽어서 상태를 만든다.**

**인스턴스는 아직 하나다.** 여기까지는 순서 문제가 발생하지 않는다.
4단계에서 인스턴스를 늘리는 순간 깨질 것이며, 그 대비를 이 티켓에서 갖춘다.

## 목표

`ledger-events`를 구독해 사용자별 잔액을 계산하고 `balancedb`에 저장한다.

## 작업 내용

- [ ] `balance-worker` 의존성 추가 (JPA, PostgreSQL 드라이버)
- [ ] `UserBalance` 엔티티 + Repository
- [ ] upsert 구현 (`INSERT ... ON CONFLICT ... DO UPDATE`)
- [ ] `@KafkaListener` (`balance-group`, `auto-offset-reset: earliest`)
- [ ] 이벤트 타입 분기 — 적립 `+`, 사용 `−`
- [ ] **잔액 부족 시 거절** 처리 + 거절 횟수 기록
- [ ] `GET /api/balance` — 사용자별 잔액 조회
- [ ] 단위 테스트 (분기 · 거절 · upsert)

## 상세 설계

### 잔액 부족 거절이 순서 의존성을 만든다

```
잔액 1,000원
① 5,000 적립   ② 3,000 사용

①→② :  1,000 → 6,000 → 3,000   ✅
②→① :  1,000 → [부족, 거절] → 6,000   ❌
```

**거절 판정이 그 시점 잔액에 좌우되므로 순서가 바뀌면 결과가 달라진다.**
이것이 이 프로젝트가 순서 보장을 요구하는 이유이며, 4단계 실험 A 가 성립하는 근거다.

거절된 이벤트는 잔액을 바꾸지 않지만 `rejected_events` 를 증가시켜 **흔적을 남긴다.**

### `auto-offset-reset: earliest`

최초 기동 시 **이미 쌓여 있는 이벤트를 전부 읽어** 잔액을 만들어낸다.
2단계에서 발행해둔 것들이 그 재료다.

단, 이것은 **커밋된 offset 이 없을 때만** 동작한다.
재기동하면 커밋된 지점에서 이어 읽는다 — 실무 정상 동작이며 [KS-18](KS-18-worker-restart-offset.md)에서 확인한다.

### 엔티티는 `data class` 로 만들지 않는다

`equals`/`hashCode` 가 lazy 프록시와 충돌한다. `data class` 는 이벤트 DTO 전용이다.

### 멱등 처리는 아직 하지 않는다

`processed_event` 테이블은 **5단계에서 추가한다.**
재생 시 포인트가 두 배가 되는 문제를 먼저 겪어야 필요성이 몸에 남는다.

## 완료 조건 (DoD)

- [ ] 워커 기동 시 기존 이벤트를 읽어 잔액이 채워진다
- [ ] 새 거래 발행 → 잔액에 즉시 반영
- [ ] 잔액보다 큰 금액 사용 시 **거절**되고 잔액이 변하지 않는다
- [ ] `GET /api/balance` 응답 확인
- [ ] Kafka UI 에서 `balance-group` 이 보이고 `gateway-group` 과 offset 이 다르다

## 배운 것

_(작업 후 작성)_

## 참고

- [04. 작업 로드맵 — 3단계](../planning/04-roadmap.md)
- [02. 설계서 — 데이터 모델](../planning/02-architecture.md)
