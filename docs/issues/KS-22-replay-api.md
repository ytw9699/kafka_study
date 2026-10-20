# KS-22: 재생 API — rebuild / replay

[← 문서 목록](../README.md) · [README](../../README.md)

| | |
|---|---|
| **타입** | FEAT |
| **상태** | DONE |
| **단계** | 5단계 |
| **브랜치** | `feature/KS-22-replay-api` |
| **PR** | [#29](https://github.com/ytw9699/kafka_study/pull/29) |

## 배경

[KS-18](KS-18-worker-restart-offset.md)에서 확인했듯 **재기동은 이어읽기**다.
`auto-offset-reset: earliest` 는 커밋된 offset 이 없을 때만 동작한다.

전체 재생을 하려면 **명시적으로 seek** 해야 한다. 이 티켓은 그 수단을 만든다.
실험은 [KS-23](KS-23-idempotency-experiment.md)에서 한다.

## 목표

워커를 살려둔 채 offset 을 처음으로 되감는 API 두 개를 만든다.

## 작업 내용

- [x] `ConsumerSeekAware` 구현 — `registerSeekCallback` 에서 콜백 보관
- [x] 배정받은 파티션 목록 추적
- [x] `POST /api/balance/rebuild` — 잔액 **비우고** seek
- [x] `POST /api/balance/replay` — 잔액 **유지한 채** seek
- [x] `app.replay.delay-ms` 설정 (기본 0, 데모 시 50)
- [x] 워커가 여러 개일 때 각자 자기 파티션만 seek 하는지 확인
- [x] 프론트에 `rebuild` / `replay` 버튼 추가

## 상세 설계

### `KafkaConsumer` 는 thread-safe 하지 않다

REST 요청은 **HTTP 스레드**에서 오고, 컨슈머는 **리스너 스레드**에서 돈다.
REST 스레드에서 컨슈머를 직접 건드리면 안 된다.

`ConsumerSeekAware.registerSeekCallback` 에서 받은 `ConsumerSeekCallback` 을 보관해두고,
REST 요청 시 그것을 호출한다. **실제 seek 은 다음 poll 직전에 컨슈머 스레드에서 적용된다.**

### 워커가 여러 개면 각자 자기 파티션만 되감는다

`rebuild` 를 한 워커에 호출하면 **그 워커가 담당한 파티션만** 되감긴다.

이것을 어떻게 다룰지가 설계 판단이다.

| 방식 | 평가 |
|---|---|
| 각 워커에 개별 호출 | 단순하지만 사람이 전부 호출해야 함 |
| 워커를 1개로 줄이고 실행 | 실험 조건이 단순해짐 |
| 워커 간 조율 (Kafka 로 명령 전달 등) | 과도함. 이 프로젝트 범위 밖 |

**5단계 실험은 워커 1개로 진행한다.** 재생의 핵심(offset seek)을 보는 것이 목적이고,
분산 조율은 다른 주제이기 때문이다. 이 제약을 문서에 명시한다.

### `rebuild` 와 `replay` 의 차이는 한 줄이다

```
rebuild : 잔액 테이블 비움 + seekToBeginning
replay  :                    seekToBeginning
```

**그 한 줄이 "정확히 복원"과 "두 배가 됨"을 가른다.**

### 재생 속도 조절

`app.replay.delay-ms` 를 주면 이벤트마다 잠깐 쉰다.
수십 건은 수백 ms 에 끝나 화면에서 과정이 보이지 않으므로, 데모 시 50ms 정도를 준다.

## 완료 조건 (DoD)

- [x] `POST /api/balance/rebuild` 호출 시 잔액이 정확히 복원된다
- [x] `POST /api/balance/replay` 호출 시 잔액이 **두 배가 된다**
- [x] 재생 중 화면에서 잔액이 변해가는 것이 보인다
- [x] 워커를 죽이지 않고 실행된다 (CLI `--reset-offsets` 와의 차이)

## 검증 결과

기준: 워커 1개(파티션 0,1,2 전부 담당), 4명 × 7건 = 28건.

### `replay` — 잔액 유지한 채 재생

```
적용 28 → 56

user-1:   137,420   기준    68,710   → 2.00배
user-2:   143,940   기준    71,970   → 2.00배
user-3:   111,360   기준    55,680   → 2.00배
user-4:   134,820   기준    67,410   → 2.00배
```

**정확히 두 배.** 😱

### `rebuild` — 잔액 비우고 재생

```
적용 28

user-1:    68,710   기준    68,710   ✅
user-2:    71,970   기준    71,970   ✅
user-3:    55,680   기준    55,680   ✅
user-4:    67,410   기준    67,410   ✅
```

**정확히 복원.** 워커를 죽이지 않고 실행됐다.

## 배운 것

### 차이는 한 줄인데 결과가 갈린다

```
rebuild : clearAll() + seekToBeginning()
replay  :             seekToBeginning()
```

**잔액을 비우느냐 한 줄**이 "정확히 복원"과 "두 배"를 가른다.
그리고 두 배가 되는 것은 버그가 아니라 **at-least-once 의 정상 결과**다 —
방어하지 않았으니 두 번 적용된 것뿐이다.

### `ConsumerSeekAware` 는 리스너 빈이 구현해야 한다

별도 빈에 구현했더니 콜백이 한 번도 불리지 않았다.
Spring Kafka 는 **`@KafkaListener` 가 붙은 빈**이 그 인터페이스를 구현했는지 확인한다.

### 콜백이 두 종류이고 스레드 안전성이 정반대다

| 출처 | 구현체 | 동작 |
|---|---|---|
| `registerSeekCallback` | `ListenerConsumer` | **지연 seek** — 다음 poll 직전 적용 |
| `onPartitionsAssigned` | `InitialOrIdleSeekCallback` | **즉시 seek** — 컨슈머 직접 호출 |

둘 다 `ConsumerSeekCallback` 타입이라 구분 없이 썼다가
`ConcurrentModificationException: KafkaConsumer is not safe for multi-threaded access` 를 만났다.

**타입이 같아도 구현체의 계약이 다를 수 있다.** → [TS-3](../troubleshooting/TS-3-seek-callback.md)

### 설계 문서가 원인을 좁혀줬다

[02. 설계서](../planning/02-architecture.md)에 **"`registerSeekCallback` 에서 받은 콜백을 보관"** 이라고
정확히 써 있었는데, 구현하면서 `onPartitionsAssigned` 의 콜백으로 덮어써버렸다.

문서를 다시 읽고 원인을 찾았다. **미리 적어둔 구현 노트가 값을 했다.**

### 워커가 여러 개면 각자에게 호출해야 한다

응답의 `partitions` 가 **그 워커가 되감은 파티션**이다.
워커 1개일 때는 `[0, 1, 2]` 전부지만, 3개면 각자 자기 것만 되감는다.

분산 재생 조율은 이 프로젝트 범위 밖이므로 **5단계는 워커 1개로 진행**한다.
재생의 핵심(offset seek)을 보는 것이 목적이기 때문이다.

## 참고

- [04. 작업 로드맵 — 5단계](../planning/04-roadmap.md)
- [KS-18 — 재기동은 이어읽기다](KS-18-worker-restart-offset.md)
