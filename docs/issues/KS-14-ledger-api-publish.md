# KS-14: ledger-api — 거래 발행 및 SSE 게이트웨이

| | |
|---|---|
| **타입** | FEAT |
| **상태** | TODO |
| **단계** | 2단계 |
| **브랜치** | `feature/KS-14-ledger-api-publish` |
| **PR** | - |

## 배경

이후 모든 단계의 재료가 되는 **이벤트를 만들어내는** 서비스.
동시에 **화면이 흐름을 볼 수 있게** SSE로 중계한다.

## 목표

`POST /api/tx`로 포인트 거래를 `ledger-events`에 발행하고, 그것이 화면에 실시간으로 흐르게 한다.

## 작업 내용

- [ ] `POST /api/tx` — `ledger-events`에 발행 (키 = `userId`)
- [ ] 응답에 `partition` / `offset` / `keyMode` 포함
- [ ] `POST /api/tx/burst` — 연속 거래 생성 (학습용)
- [ ] `app.partition-key-mode` 스위치 (`USER_ID` / `RANDOM`) — **4단계 실험 A의 조작 장치**
- [ ] `POST /api/config/partition-key-mode` — 런타임 전환
- [ ] SSE 게이트웨이 — `gateway-group`으로 **전체 파티션** 구독 → `GET /api/stream`
- [ ] `SseEmitter` 생명주기 관리 (`onCompletion`/`onTimeout`/`onError`에서 제거)

## 상세 설계

### DB를 갖지 않는다

`ledger-api`는 Postgres에 붙지 않는다. **Kafka가 원장의 원본(source of truth)이다.**
요청을 DB에 저장하면 "로그가 원본이고 나머지는 파생 상태"라는 이 프로젝트의 전제가 흐려진다.

### SSE를 여기에 두는 이유

`balance-worker`는 4단계에서 여러 인스턴스로 늘어난다. 거기에 SSE를 붙이면
브라우저가 **그중 하나에만 연결**되어 자기 파티션 이벤트만 보게 된다.

단일 인스턴스인 `ledger-api`가 **별도 컨슈머 그룹**(`gateway-group`)으로 전체 파티션을 구독해 중계하면 이 문제가 없다.

> 우회가 아니라 실무 패턴이다. **작업 분배(컨슈머 그룹)와 브로드캐스트(SSE)는 다른 레이어**이므로 분리한다.
> 0단계 CLI 실습에서 확인한 "같은 그룹 = 분배 / 다른 그룹 = 둘 다 받음"이 여기서 설계로 쓰인다.

### partition-key-mode 스위치

4단계 실험 A에서 **순서가 깨지면 잔액이 틀린다**를 재현하기 위한 장치.

| 모드 | 키 | 결과 |
|---|---|---|
| `USER_ID` (기본) | `userId` | 같은 사용자는 같은 파티션 → 순서 보장 |
| `RANDOM` | 랜덤 UUID | 여러 파티션에 흩어짐 → **순서 깨짐** |

프로덕션에는 없을 설정이지만, 설정 한 줄로 재현·복구가 되므로 학습 비용이 거의 없다.

### burst API가 필요한 이유

순서 문제가 드러나려면 **한 사용자의 거래가 촘촘히 이어져야** 한다.
손으로 하나씩 넣으면 처리 간격이 벌어져 경쟁 상태가 잘 안 생긴다.

```json
POST /api/tx/burst
{ "users": 5, "txPerUser": 20 }
```

### 응답에 partition/offset을 넣는 대가

`KafkaTemplate.send()`는 비동기(`CompletableFuture`)다. 이 값을 응답에 담으려면 결과를 기다려야 하므로 **응답 지연이 생긴다.** 학습 목적이므로 감수하되 한계는 인지한다.

## 완료 조건 (DoD)

- [ ] `POST /api/tx` → 202 + `partition`/`offset` 응답
- [ ] Kafka UI에서 `ledger-events`에 메시지가 쌓임
- [ ] **같은 `userId`로 5건 → 전부 같은 파티션**
- [ ] `RANDOM` 모드로 전환 시 **같은 userId가 여러 파티션에 흩어짐**
- [ ] `curl -N localhost:8081/api/stream`으로 이벤트 수신
- [ ] `burst`로 100건 생성 시 SSE로 전부 흘러나옴

## 배운 것

_(작업 후 작성)_

## 참고

- [04. 작업 로드맵 — 2단계](../planning/04-roadmap.md)
- [02. 설계서 — 서비스별 책임 / API 명세](../planning/02-architecture.md)
