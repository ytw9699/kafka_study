# KS-6: order-service — 주문 생성 및 이벤트 발행

| | |
|---|---|
| **타입** | FEAT |
| **상태** | TODO |
| **단계** | 1단계 — Producer |
| **브랜치** | `feature/KS-6-order-service-producer` |
| **PR** | - |

## 배경

이후 모든 단계의 재료가 되는 **이벤트를 만들어내는** 서비스. 데이터가 있어야 나머지가 성립한다.

## 목표

`POST /api/orders`로 주문을 저장하고 Kafka에 발행한다.
**같은 `userId`의 주문이 항상 같은 파티션에 들어가는 것**을 Kafka UI에서 확인한다.

## 작업 내용

- [ ] `Order` 엔티티 + Repository (`orderdb`)
- [ ] `POST /api/orders` — 저장 후 `KafkaTemplate`으로 발행 (키 = `userId`)
- [ ] 응답에 `partition` / `offset` 포함
- [ ] `GET /api/orders` — 원본 주문 목록
- [ ] 같은 `userId`로 반복 주문해 파티션 고정 확인

## 상세 설계

### 파티션 키를 `userId`로 잡는 이유

Kafka는 **파티션 내부에서만** 순서를 보장한다. 토픽 전체 순서는 보장하지 않는다.
`userId`를 키로 쓰면 같은 사용자의 이벤트가 항상 같은 파티션에 들어가므로 "한 사용자 관점의 주문 순서"가 지켜진다.

### 응답에 partition/offset을 넣는 이유

실무에서는 불필요하다. **학습 목적**이다 — 화면에서 "내 주문이 어느 파티션 몇 번 offset에 들어갔는지"를 바로 보기 위함이며, 2단계 피드의 뱃지로 이어진다.

`KafkaTemplate.send()`는 비동기(`CompletableFuture`)이므로 이 값을 응답에 담으려면 결과를 기다려야 한다.
학습용으로는 문제없지만, **응답 지연이 생긴다는 것은 인지한다.**

### 범위 밖

DB 저장과 Kafka 발행 사이의 원자성(트랜잭셔널 아웃박스)은 다루지 않는다.
저장은 됐는데 발행이 실패하는 경우가 있을 수 있다는 한계만 인지한다.

## 완료 조건 (DoD)

- [ ] `POST /api/orders` → 201 + `partition`/`offset` 응답
- [ ] Kafka UI에서 `order-events`에 메시지가 쌓이는 것 확인
- [ ] **같은 `userId`로 5건 주문 → 전부 같은 파티션**
- [ ] 다른 `userId`로 주문 → 다른 파티션에도 분산됨
- [ ] Postgres `orders` 테이블에 원본 저장 확인

## 배운 것

_(작업 후 작성)_

## 참고

- [04. 작업 로드맵 — 1단계](../planning/04-roadmap.md)
- [02. 설계서 — 토픽 설계 / 이벤트 스키마 / API 명세](../planning/02-architecture.md)
