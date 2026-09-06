# KS-19: 워커 인스턴스 확장 + 파티션 배정 화면

| | |
|---|---|
| **타입** | FEAT |
| **상태** | TODO |
| **단계** | 4단계 |
| **브랜치** | `feature/KS-19-worker-scale-out` |
| **PR** | - |

## 배경

4단계의 두 실험(순서 깨짐 · 리밸런싱)은 모두 **워커가 여러 개**여야 성립한다.
그리고 무슨 일이 일어나는지 보려면 **누가 어느 파티션을 맡았는지**가 화면에 보여야 한다.

이 티켓은 **관찰 수단**을 만든다. 실험 자체는 [KS-20](KS-20-order-broken-experiment.md), [KS-21](KS-21-rebalance-experiment.md)에서 한다.

## 목표

워커를 여러 개 띄우고, 파티션 배정 현황을 화면에서 실시간으로 본다.

## 작업 내용

- [ ] `WorkerAssignment` 엔티티 + Repository (`balancedb`)
- [ ] `ConsumerRebalanceListener` — 배정/해제 시 기록
- [ ] 워커 식별자 `workerId` (포트 기반, 예: `w-8082`)
- [ ] 하트비트 갱신 (주기적으로 `last_heartbeat` 업데이트)
- [ ] `GET /api/workers` — 워커별 담당 파티션
- [ ] `PartitionBoard.vue` — 워커별 파티션 뱃지 (피드와 같은 색상 체계)
- [ ] 죽은 워커 표시 (하트비트가 오래된 경우 흐리게)
- [ ] 워커 3개 기동해 파티션이 나뉘는지 확인

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

- [ ] 워커 3개 기동 시 각자 파티션 1개씩 배정받는다
- [ ] `GET /api/workers` 로 배정 현황이 조회된다
- [ ] 화면에서 워커별 담당 파티션이 보인다
- [ ] 워커를 하나 죽이면 하트비트가 멈추고 화면에서 흐려진다

## 배운 것

_(작업 후 작성)_

## 참고

- [04. 작업 로드맵 — 4단계](../planning/04-roadmap.md)
- [02. 설계서 — worker_assignment](../planning/02-architecture.md)
