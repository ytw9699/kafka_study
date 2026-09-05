# KS-14: ledger-api — 거래 발행 및 SSE 게이트웨이

| | |
|---|---|
| **타입** | FEAT |
| **상태** | IN REVIEW |
| **단계** | 2단계 |
| **브랜치** | `feature/KS-14-ledger-api-publish` |
| **PR** | [#17](https://github.com/ytw9699/kafka_study/pull/17) |

## 배경

이후 모든 단계의 재료가 되는 **이벤트를 만들어내는** 서비스.
동시에 **화면이 흐름을 볼 수 있게** SSE로 중계한다.

## 목표

`POST /api/tx`로 포인트 거래를 `ledger-events`에 발행하고, 그것이 화면에 실시간으로 흐르게 한다.

## 작업 내용

- [x] `POST /api/tx` — `ledger-events`에 발행 (키 = `userId`)
- [x] 응답에 `partition` / `offset` / `keyMode` 포함
- [x] `POST /api/tx/burst` — 연속 거래 생성 (학습용)
- [x] `app.partition-key-mode` 스위치 (`USER_ID` / `RANDOM`) — **4단계 실험 A의 조작 장치**
- [x] `POST /api/config/partition-key-mode` — 런타임 전환
- [x] SSE 게이트웨이 — `gateway-group`으로 **전체 파티션** 구독 → `GET /api/stream`
- [x] `SseEmitter` 생명주기 관리 (`onCompletion`/`onTimeout`/`onError`에서 제거)

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

- [x] `POST /api/tx` → 202 + `partition`/`offset` 응답
- [x] Kafka UI에서 `ledger-events`에 메시지가 쌓임
- [x] **같은 `userId`로 5건 → 전부 같은 파티션**
- [x] `RANDOM` 모드로 전환 시 **같은 userId가 여러 파티션에 흩어짐**
- [x] `curl -N localhost:8081/api/stream`으로 이벤트 수신
- [x] `burst`로 100건 생성 시 SSE로 전부 흘러나옴

## 검증 결과

### 같은 `userId` → 파티션 고정

```
POST /api/tx  {"userId":"user-42","type":"EARN",...}  × 5

  partition=1 offset=9  key=user-42
  partition=1 offset=10 key=user-42
  partition=1 offset=11 key=user-42
  partition=1 offset=12 key=user-42
  partition=1 offset=13 key=user-42
```

### 다른 사용자는 분산

```
  alice → partition=0
  bob   → partition=0
  carol → partition=2
```

### `RANDOM` 모드 — 같은 사용자가 흩어진다

```
POST /api/config/partition-key-mode  {"mode":"RANDOM"}
POST /api/tx  {"userId":"user-42",...}  × 6

  partition=2  key=59c97e1a...
  partition=0  key=ab6a6bd1...
  partition=1  key=f71e1070...
  partition=0  key=0f986cc0...
  partition=1  key=388169b4...
  partition=1  key=2482aff0...
```

**4단계 실험 A 의 조건이 갖춰졌다.** 이 상태에서 워커를 여러 개 띄우면 잔액이 틀어진다.

### SSE 중계

```
수신 이벤트 수: 9
data:{"eventId":"...","eventType":"POINT_EARNED","userId":"bob",
      "amount":5000,"partition":0,"offset":8}
```

### burst

```
POST /api/tx/burst  {"users":5,"txPerUser":10}

{ "published": 55, "keyMode": "USER_ID",
  "byPartition": { "1": 22, "2": 33 } }
```

## 배운 것

### 해시 편중이 실제로 나타났다

burst 로 5명의 사용자를 만들었는데 **파티션 2개에만 몰렸다.** 0번 파티션은 비어 있다.

[KS-3](KS-3-kafka-cli-basics.md)의 CLI 실습에서 본 것과 같은 현상이며, 우연이 아니라 구조적 특성이다.
사용자 수가 적으면 murmur2 해시가 파티션을 고르게 채우지 못한다.

→ 4단계에서 워커를 3개 띄워도 **한 워커는 놀게 된다.**
→ 7단계의 "한 사용자의 실패가 같은 파티션의 다른 사용자를 막는다"가 더 잘 드러날 조건이기도 하다.

### SSE 를 어디에 두느냐가 설계 결정이었다

`balance-worker` 에 SSE 를 붙였다면 4단계에서 인스턴스가 늘어나는 순간
브라우저가 **그중 하나에만 연결되어 자기 파티션 이벤트만** 보게 된다.

단일 인스턴스인 `ledger-api` 가 **별도 컨슈머 그룹**으로 전체를 구독하면 해결된다.

**0단계 CLI 실습에서 확인한 "다른 그룹이면 둘 다 받고, 같은 그룹이면 나눠 갖는다"가
여기서 그대로 설계 근거로 쓰였다.** 개념 실습이 실제 판단으로 이어진 첫 지점이다.

### `spring.json.add.type.headers=false` 를 꺼야 하는 이유

Spring Kafka 의 `JsonSerializer` 는 기본적으로 **자바 클래스명을 메시지 헤더에 싣는다.**

그대로 두면
1. 컨슈머 쪽 패키지 구조가 프로듀서와 같아야 한다 — **서비스 간 결합**
2. 우리는 이미 본문의 `eventType` 으로 타입을 판별하므로 **중복**

컨슈머 쪽에서도 `spring.json.use.type.headers=false` + `default.type` 을 지정해
**본문만 보고 역직렬화**하게 했다.

### `send().get()` 은 학습용 선택이다

응답에 `partition`/`offset` 을 담으려면 브로커 ack 을 기다려야 한다.
`KafkaTemplate.send()` 는 비동기이므로 `.get()` 으로 블로킹했다.

화면에서 "내 거래가 어느 파티션에 들어갔는지"를 바로 보는 것이 이 프로젝트에 중요하므로 감수하지만,
**실무에서는 이 대기가 그대로 응답 지연**이 된다. 보통은 콜백으로 처리한다.

### `SseEmitter` 정리를 빠뜨리면 메모리가 샌다

`onCompletion` / `onTimeout` / `onError` 에서 목록에서 제거해야 한다.
추가로 전송 시 `IOException`(브라우저가 이미 닫음)을 잡아 정리한다.

Kafka 리스너 스레드가 순회하고 HTTP 스레드가 추가/제거하므로 `CopyOnWriteArrayList` 를 썼다.

### `202 Accepted` 가 맞는 응답이다

`201 Created` 가 아니다. **발행만 했을 뿐 잔액에 반영된 것이 아니기 때문이다.**
반영은 `balance-worker` 가 비동기로 한다.

이벤트 드리븐에서 "요청을 접수했다"와 "처리가 끝났다"는 다르고, 이 차이가 상태 코드에 드러난다.

## 참고

- [04. 작업 로드맵 — 2단계](../planning/04-roadmap.md)
- [02. 설계서 — 서비스별 책임 / API 명세](../planning/02-architecture.md)
