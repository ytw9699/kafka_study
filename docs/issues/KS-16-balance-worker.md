# KS-16: balance-worker — 잔액 계산 컨슈머

[← 문서 목록](../README.md) · [README](../../README.md)

| | |
|---|---|
| **타입** | FEAT |
| **상태** | DONE |
| **단계** | 3단계 |
| **브랜치** | `feature/KS-16-balance-worker` |
| **PR** | [#20](https://github.com/ytw9699/kafka_study/pull/20) |

## 배경

지금까지는 이벤트를 **발행하고 보기만** 했다. 이제 그것을 **읽어서 상태를 만든다.**

**인스턴스는 아직 하나다.** 여기까지는 순서 문제가 발생하지 않는다.
4단계에서 인스턴스를 늘리는 순간 깨질 것이며, 그 대비를 이 티켓에서 갖춘다.

## 목표

`ledger-events`를 구독해 사용자별 잔액을 계산하고 `balancedb`에 저장한다.

## 작업 내용

- [x] `balance-worker` 의존성 추가 (JPA, PostgreSQL 드라이버)
- [x] `UserBalance` 엔티티 + Repository
- [x] upsert 구현 (`INSERT ... ON CONFLICT ... DO UPDATE`)
- [x] `@KafkaListener` (`balance-group`, `auto-offset-reset: earliest`)
- [x] 이벤트 타입 분기 — 적립 `+`, 사용 `−`
- [x] **잔액 부족 시 거절** 처리 + 거절 횟수 기록
- [x] `GET /api/balance` — 사용자별 잔액 조회
- [x] 단위 테스트 (분기 · 거절 · upsert)

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
재생 시 포인트가 두 배가 되는 문제를 먼저 겪어야 왜 필요한지 확실히 알게 된다.

## 완료 조건 (DoD)

- [x] 워커 기동 시 기존 이벤트를 읽어 잔액이 채워진다
- [x] 새 거래 발행 → 잔액에 즉시 반영
- [x] 잔액보다 큰 금액 사용 시 **거절**되고 잔액이 변하지 않는다
- [x] `GET /api/balance` 응답 확인
- [x] Kafka UI 에서 `balance-group` 이 보이고 `gateway-group` 과 offset 이 다르다

## 검증 결과

### 워커를 나중에 켜서 과거를 전부 읽는다 (`earliest`)

`ledger-api` 만 띄워 18건을 쌓아둔 뒤 워커를 **처음** 기동:

```json
{
  "balances": [
    { "userId": "user-1", "balance": 70530, "appliedEvents": 6, "rejectedEvents": 0 },
    { "userId": "user-2", "balance": 69370, "appliedEvents": 6, "rejectedEvents": 0 },
    { "userId": "user-3", "balance": 75600, "appliedEvents": 6, "rejectedEvents": 0 }
  ],
  "totalApplied": 18, "totalRejected": 0
}
```

**18건 전부 반영됐다.**

### 실시간 반영 · 거절

```
+10,000 적립 → user-1 잔액: 80,530 (applied=7)
99,999,999 사용 시도 → 잔액: 80,530  거절=1  applied=7
```

```
WARN  거절 userId=user-1 잔액=80530 요청=99999999 (부족)
INFO  거절 p2#19 user-1 잔액=80530 요청=99999999
```

**거절되면 잔액은 변하지 않고 흔적만 남는다.**

### 컨슈머 그룹별 독립 offset

```
gateway-group  p2  cur=20  end=20  lag=0
balance-group  p2  cur=20  end=20  lag=0
```

각자 커밋한다. 같은 메시지를 **둘 다** 받았다.

### 단위 테스트 5건

```
✅ 적립은 잔액을 늘린다
✅ 사용은 잔액을 줄인다
✅ 잔액이 부족하면 거절되고 잔액은 변하지 않는다
✅ 사용자별로 잔액이 독립적이다
✅ 순서가 바뀌면 결과가 달라진다        ← 4단계의 근거
```

## 배운 것

### 파티션 키는 순서 보장뿐 아니라 **동시성 안전**도 준다

처음에는 설계서대로 `INSERT ... ON CONFLICT` 원자적 upsert 를 쓰려 했다. 그런데 필요가 없었다.

파티션 키가 `userId` 이고 **한 파티션은 한 워커만** 처리하므로,
같은 사용자의 거래는 언제나 **한 워커가 순서대로** 다룬다. **경합 자체가 발생하지 않는다.**

| 얻는 것 | 근거 |
|---|---|
| 순서 보장 | 파티션 내부 순서 + 단일 컨슈머 |
| 동시성 안전 | 같은 키 = 같은 파티션 = 같은 워커 |

**둘이 같은 뿌리에서 나온다.** "파티션 키를 잘 잡으면 락이 필요 없다"는 것이
Kafka 기반 설계의 큰 이점이며, 이번에 코드로 확인했다.

> 단, 4단계에서 키를 `RANDOM` 으로 바꾸면 이 전제가 깨진다.
> 순서도 깨지고 **같은 사용자 행을 두 워커가 동시에 건드리는 lost update** 도 생긴다.
> 잔액이 틀어지는 원인이 하나가 아니라 둘이 되는 셈이다.

### 역직렬화 실패는 애플리케이션 코드로 잡을 수 없다 (TS-2)

워커를 처음 켰을 때 잔액이 비어 있었다. [KS-3](KS-3-kafka-cli-basics.md) CLI 실습 때 넣은
`{"n":1}` 같은 원시 JSON 이 토픽에 남아 있었고, `eventType` 이 없어 역직렬화가 실패했다.

**리스너에 도달하기 전 단계에서 실패**하므로 `try-catch` 로 잡을 수 없다.
컨슈머는 그 offset 에서 멈춰 무한 재시도한다 — **poison pill**.

메시지 하나가 파티션 전체를 영구히 막는다는 것을 7단계 전에 미리 겪었다.
→ [TS-2](../troubleshooting/TS-2-poison-pill.md)

### 실습용 토픽과 애플리케이션 토픽을 나눴어야 했다

CLI 실습과 앱이 같은 토픽을 공유한 것이 화근이었다.
실무에서도 스키마가 다른 메시지를 한 토픽에 섞으면 컨슈머가 언제든 멈춘다.

### `earliest` 는 "최초 기동"에서만 발동한다

이번에 워커를 처음 켜면서 과거 18건을 읽는 것을 확인했다.
다만 이것은 **커밋된 offset 이 없었기 때문**이고, 재기동하면 이어서 읽는다.
→ [KS-18](KS-18-worker-restart-offset.md)에서 직접 확인한다.

### 빈 파티션의 offset 표시가 그룹마다 달랐다

```
gateway-group  p0 cur=0   (latest 로 시작 → 위치 0 을 커밋)
balance-group  p0 cur=-   (earliest 지만 읽은 게 없어 커밋 없음)
```

`-` 는 "아직 커밋한 적 없음"이다. 사소하지만 offset 이 **소비할 때 비로소 생긴다**는 것을 보여준다.

## 참고

- [04. 작업 로드맵 — 3단계](../planning/04-roadmap.md)
- [02. 설계서 — 데이터 모델](../planning/02-architecture.md)
