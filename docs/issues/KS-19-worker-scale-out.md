# KS-19: 워커 인스턴스 확장 + 파티션 배정 화면

[← 문서 목록](../README.md) · [README](../../README.md)

| | |
|---|---|
| **타입** | FEAT |
| **상태** | DONE |
| **단계** | 4단계 |
| **브랜치** | `feature/KS-19-worker-scale-out` |
| **PR** | [#24](https://github.com/ytw9699/kafka_study/pull/24) |

## 배경

4단계의 두 실험(순서 깨짐 · 리밸런싱)은 모두 **워커가 여러 개**여야 성립한다.
그리고 무슨 일이 일어나는지 보려면 **누가 어느 파티션을 맡았는지**가 화면에 보여야 한다.

이 티켓은 **관찰 수단**을 만든다. 실험 자체는 [KS-20](KS-20-order-broken-experiment.md), [KS-21](KS-21-rebalance-experiment.md)에서 한다.

## 목표

워커를 여러 개 띄우고, 파티션 배정 현황을 화면에서 실시간으로 본다.

## 작업 내용

- [x] `WorkerAssignment` 엔티티 + Repository (`balancedb`)
- [x] `ConsumerRebalanceListener` — 배정/해제 시 기록
- [x] 워커 식별자 `workerId` (포트 기반, 예: `w-8082`)
- [x] 하트비트 갱신 (주기적으로 `last_heartbeat` 업데이트)
- [x] `GET /api/workers` — 워커별 담당 파티션
- [x] `PartitionBoard.vue` — 워커별 파티션 뱃지 (피드와 같은 색상 체계)
- [x] 죽은 워커 표시 (하트비트가 오래된 경우 흐리게)
- [x] 워커 3개 기동해 파티션이 나뉘는지 확인

## 상세 설계

### `ConsumerRebalanceListener`

Kafka 가 파티션을 (재)배정할 때 훅이 걸린다.

| 콜백 | 시점 |
|---|---|
| `onPartitionsRevoked` | 소유권을 **넘기기 직전** — 정리할 기회 |
| `onPartitionsAssigned` | 새로 **배정받은 직후** |

배정 정보를 DB 에 기록해두면 프론트가 폴링으로 읽을 수 있다.

### 왜 DB 에 기록하는가

워커는 여러 인스턴스다. 각자 메모리에 들고 있으면 **어느 인스턴스에 물어보느냐에 따라 답이 달라진다.**
공유 DB 에 쓰면 어느 인스턴스에 물어도 전체 그림이 나온다.

> [KS-17](KS-17-balance-panel.md)에서 잔액을 폴링으로 가져온 것과 같은 이유다.

### 하트비트가 필요한 이유

워커를 `kill` 하면 `onPartitionsRevoked` 가 불리지 않는다 (프로세스가 그냥 죽으므로).
따라서 **DB 에는 죽은 워커의 배정이 그대로 남는다.**

하트비트를 주기적으로 갱신하고, 오래된 워커는 화면에서 흐리게 처리한다.
Kafka 도 같은 방식으로 컨슈머 생존을 판단한다 (`session.timeout.ms`).

> 이것 자체가 학습 포인트다 — **분산 시스템에서 "죽었다"를 아는 유일한 방법은 "한동안 소식이 없다"뿐이다.**

### 워커 여러 개 띄우기

```bash
SERVER_PORT=8082 ./gradlew :balance-worker:bootRun
SERVER_PORT=8092 ./gradlew :balance-worker:bootRun
SERVER_PORT=8093 ./gradlew :balance-worker:bootRun
```

같은 `balance-group` 이므로 파티션 3개가 나눠 배정된다.

## 완료 조건 (DoD)

- [x] 워커 3개 기동 시 각자 파티션 1개씩 배정받는다
- [x] `GET /api/workers` 로 배정 현황이 조회된다
- [x] 화면에서 워커별 담당 파티션이 보인다
- [x] 워커를 하나 죽이면 하트비트가 멈추고 화면에서 흐려진다

## 검증 결과

### 워커 3개 → 파티션 1개씩

```json
{ "workers": [
    { "workerId": "w-8082", "partitions": [1], "generation": 2, "alive": true },
    { "workerId": "w-8092", "partitions": [2], "generation": 1, "alive": true },
    { "workerId": "w-8093", "partitions": [0], "generation": 1, "alive": true } ],
  "aliveCount": 3, "assignedPartitions": [0, 1, 2] }
```

### `kill -9` 직후 (12초) — 아직 리밸런싱 전

```
살아있는 워커: 2   담당 파티션: [1, 2]      ← p0 이 비었다
  w-8082: [1] alive=true
  w-8092: [2] alive=true
  w-8093: [0] alive=false (12s)           ← 배정 기록은 남아 있다
```

**`onPartitionsRevoked` 가 불리지 않아 죽은 워커의 배정이 그대로 남았다.**
그동안 **p0 은 아무도 처리하지 않는다.**

### 약 37초 후 — 리밸런싱 완료

```
w-8082: [0, 1] gen=3      ← p0 을 인수받고 세대가 올라갔다
w-8092: [2]    gen=2
w-8093: [0]    alive=false
```

### 워커 4개 (파티션 3개)

```
w-8082: [1]
w-8092: [2]
w-8093: []    ← 놀고 있음
w-8094: [0]
```

## 배운 것

### `kill -9` 는 `onPartitionsRevoked` 를 부르지 않는다

| | `onPartitionsRevoked` | 리밸런싱 시작 |
|---|---|---|
| 정상 종료 | 호출됨 | 즉시 |
| `kill -9` | **호출 안 됨** | `session.timeout` 후 |

프로세스가 그냥 사라지므로 정리할 기회가 없다.
**DB 에는 죽은 워커의 배정이 그대로 남고**, 화면은 그것을 사실대로 보여줘야 한다.

### "죽었다"를 아는 유일한 방법은 "한동안 소식이 없다"뿐이다

이것이 하트비트가 필요한 이유이고, Kafka 가 `session.timeout.ms` 를 두는 이유다.

그리고 **감지 시간과 오탐의 트레이드오프**가 여기서 생긴다.
짧게 잡으면 GC 로 잠깐 멈춘 워커까지 죽었다고 판단해 불필요한 리밸런싱이 일어나고,
길게 잡으면 이번처럼 **약 37초 동안 p0 이 방치**된다.

> [05. 문서](../planning/05-why-not-diy.md)의 split-brain 이 바로 이 지점에서 생긴다.
> "죽었다고 판단했는데 사실 살아 있었다"가 이중 처리로 이어진다.

### 리밸런싱 중에는 담당자 없는 파티션이 생긴다

`assignedPartitions` 에서 `p0` 이 빠진 구간이 실제로 관찰됐다.

**이 시간 동안 그 파티션의 이벤트는 처리되지 않는다.** 유실은 아니고 지연이다 —
재배정 후 밀린 것을 전부 읽는다 ([KS-18](KS-18-worker-restart-offset.md)에서 확인한 성질).

### `generation` 이 리밸런싱 횟수를 드러낸다

`w-8082` 의 세대가 `2 → 3` 으로 올라갔다.
Kafka 컨슈머 그룹의 generation id 와 같은 개념이며, **fencing token 의 역할**을 한다.
옛 세대의 offset 커밋은 브로커가 거부한다.

직접 만들었다면 이것부터 설계해야 했을 것이다 → [05. 문서](../planning/05-why-not-diy.md)

### 워커를 늘려도 파티션 수를 넘으면 소용없다

`w-8093` 이 파티션을 못 받고 놀았다.
[KS-3](KS-3-kafka-cli-basics.md) CLI 실습에서 본 것을 **애플리케이션 수준에서 재확인**했다.

**파티션 수는 나중에 늘릴 수는 있어도 줄일 수 없으므로** 처음부터 여유 있게 잡아야 한다.
그리고 [KS-14](KS-14-ledger-api-publish.md)에서 본 **해시 편중**까지 겹치면,
파티션이 남아도 특정 워커에만 부하가 몰릴 수 있다.

## 참고

- [04. 작업 로드맵 — 4단계](../planning/04-roadmap.md)
- [02. 설계서 — worker_assignment](../planning/02-architecture.md)
