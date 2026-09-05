# 02. 설계서

## 1. 전체 구조

```
                      ┌───────────────────────────────────────────┐
                      │           프론트엔드 (Vue 3)               │
                      │  주문 생성 폼 │ 실시간 피드 │ 매출 통계 차트 │
                      └───┬─────────────────▲──────────────▲──────┘
                          │                 │              │
                    POST /orders      SSE /stream    SSE /stream
                          │                 │              │
              ┌───────────▼──────┐  ┌───────┴──────┐  ┌────┴─────────┐
              │  order-service   │  │ feed-service │  │ stats-service│
              │     :8081        │  │    :8082     │  │    :8083     │
              │   (Producer)     │  │ (Consumer A) │  │ (Consumer B) │
              │                  │  │              │  │              │
              │  H2: orders      │  │  stateless   │  │  H2: 집계    │
              └───────────┬──────┘  └───────▲──────┘  └────▲─────────┘
                          │                 │              │
                     publish             subscribe      subscribe
                          │            group: feed    group: stats
                          │          (offset: latest) (offset: earliest)
                          │                 │              │
                    ┌─────▼─────────────────┴──────────────┴─────┐
                    │        Kafka  ─  topic: order-events        │
                    │        partitions: 3  │  key: userId        │
                    │              :9092 (KRaft)                  │
                    └─────────────────────────────────────────────┘
                                          │
                                   ┌──────▼──────┐
                                   │  Kafka UI   │
                                   │    :8080    │
                                   └─────────────┘
```

### 핵심 설계 포인트

**두 컨슈머는 서로 다른 컨슈머 그룹이다.**
같은 토픽을 각자의 offset으로 **독립적으로** 읽는다. 이것이 큐와 구조적으로 다른 지점이며, 3단계 하이라이트가 성립하는 이유다.

| 서비스 | 컨슈머 그룹 | `auto-offset-reset` | 의미 |
|---|---|---|---|
| feed-service | `feed-group` | `latest` | 기동 이후 들어온 주문만 본다 (실시간 피드니까) |
| stats-service | `stats-group` | `earliest` | **기동 시점에 쌓여 있던 과거 주문 전부를 읽는다** |

---

## 2. 서비스별 책임

### order-service (:8081) — Producer

- 주문 생성 요청을 받아 H2에 저장
- 저장된 주문을 `order-events` 토픽에 발행
- 파티션 키는 `userId` → **같은 사용자의 주문은 같은 파티션 = 순서 보장**

> DB 저장과 Kafka 발행 사이의 원자성 문제(트랜잭셔널 아웃박스 패턴)는 이 프로젝트 범위 밖. 학습 단계에서는 단순 발행으로 진행하되, 이 한계가 존재한다는 것은 인지한다.

### feed-service (:8082) — Consumer A, 실시간 피드

- `order-events`를 `latest`부터 구독
- 수신한 이벤트를 SSE로 연결된 브라우저에 즉시 push
- **상태를 저장하지 않는다** (순수 릴레이)
- 5단계에서 인스턴스를 여러 개 띄워 파티션 분배·리밸런싱을 관찰하는 대상

### stats-service (:8083) — Consumer B, 매출 집계

- `order-events`를 `earliest`부터 구독
- 주문을 집계해 H2에 저장 (상품별 매출, 총 주문 수 등)
- 집계 결과를 REST로 조회 + 갱신 시 SSE로 push
- **처리한 `orderId`를 기록**해 replay 시 중복 집계를 방지 (4단계)

---

## 3. 토픽 설계

### `order-events`

| 항목 | 값 | 근거 |
|---|---|---|
| 파티션 수 | 3 | 5단계 리밸런싱 실습을 위해 컨슈머 인스턴스보다 많거나 같게 |
| 파티션 키 | `userId` | 같은 사용자의 주문 순서 보장. 키가 같으면 같은 파티션으로 감 |
| 복제 계수 | 1 | 로컬 단일 브로커 (운영이라면 3) |
| retention | 기본 7일 | 학습 중 재생이 가능해야 하므로 충분 |
| 값 형식 | JSON | 기초 단계. Avro/Schema Registry는 범위 밖 |

### 파티션 키를 `userId`로 잡은 이유

Kafka는 **파티션 내부에서만** 순서를 보장한다. 토픽 전체 순서는 보장하지 않는다.
`userId`를 키로 쓰면 같은 사용자의 이벤트가 항상 같은 파티션에 들어가므로, "한 사용자 관점의 주문 순서"는 지켜진다. 서로 다른 사용자 간 순서는 보장되지 않지만 비즈니스적으로 필요 없다.

> 5단계에서 이 특성을 직접 확인한다: 같은 사용자로 여러 건 주문 → 항상 같은 파티션에 적재됨.

### 추가 토픽 (6단계)

| 토픽 | 용도 |
|---|---|
| `order-events-retry` | 처리 실패 시 재시도 대상 |
| `order-events-dlt` | 재시도 소진 후 격리되는 Dead Letter Topic |

---

## 4. 이벤트 스키마

### OrderCreatedEvent

`order-events` 토픽에 발행되는 메시지 값.

```json
{
  "eventId": "9f1c2e7a-4b8d-4d5e-9a1f-2c3b4d5e6f70",
  "eventType": "ORDER_CREATED",
  "occurredAt": "2026-09-05T14:32:10.123Z",
  "orderId": "ORD-20260905-0001",
  "userId": "user-42",
  "productName": "기계식 키보드",
  "quantity": 2,
  "unitPrice": 89000,
  "totalPrice": 178000
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `eventId` | UUID | 이벤트 고유 식별자 |
| `eventType` | String | 이벤트 종류. 향후 확장 대비 |
| `occurredAt` | ISO-8601 | 이벤트 발생 시각 |
| `orderId` | String | 주문 식별자. **멱등 처리의 기준 키** |
| `userId` | String | **파티션 키** |
| `productName` | String | 상품명. 통계 집계 기준 |
| `quantity` | Int | 수량 |
| `unitPrice` | Long | 단가 (원) |
| `totalPrice` | Long | 합계 = `quantity * unitPrice` |

**메시지 키**: `userId` (String)

> `orderId`를 이벤트에 포함시키는 것이 4단계의 열쇠다. replay로 같은 메시지를 다시 받았을 때 "이미 처리한 주문인가?"를 판단할 기준이 된다.

---

## 5. API 명세

### order-service (:8081)

#### `POST /api/orders` — 주문 생성

요청:
```json
{
  "userId": "user-42",
  "productName": "기계식 키보드",
  "quantity": 2,
  "unitPrice": 89000
}
```

응답 `201 Created`:
```json
{
  "orderId": "ORD-20260905-0001",
  "totalPrice": 178000,
  "publishedToPartition": 1,
  "offset": 17
}
```

> 응답에 `partition`/`offset`을 담는 것은 학습 목적이다. 실무에서는 불필요하지만, 이 프로젝트에서는 "내 주문이 어느 파티션 몇 번 offset에 들어갔는지"를 화면에서 바로 보는 것이 이해에 크게 도움된다.

#### `GET /api/orders` — 주문 목록 조회
DB에 저장된 원본 주문 목록. Kafka 재생 결과와 비교하는 기준선.

---

### feed-service (:8082)

#### `GET /api/feed/stream` — 실시간 주문 피드 (SSE)

```
event: order
data: { "orderId": "ORD-...", "userId": "user-42", "productName": "...", "totalPrice": 178000, "partition": 1, "offset": 17 }
```

#### `GET /api/feed/recent` — 최근 수신 이벤트 (메모리 버퍼)
SSE 연결 전에 받은 것들을 화면 초기 렌더링용으로 제공.

---

### stats-service (:8083)

#### `GET /api/stats` — 현재 집계 조회

```json
{
  "totalOrders": 42,
  "totalRevenue": 3_820_000,
  "processedEventCount": 42,
  "byProduct": [
    { "productName": "기계식 키보드", "orderCount": 12, "revenue": 1068000 },
    { "productName": "모니터암",     "orderCount": 30, "revenue": 2752000 }
  ]
}
```

#### `GET /api/stats/stream` — 집계 갱신 실시간 push (SSE)
과거 이벤트를 재생하며 집계가 채워지는 과정을 프론트에서 실시간으로 보기 위한 채널. **3단계 하이라이트의 시각화 수단.**

#### `POST /api/stats/reset` — 집계 초기화 (학습용)
4단계 replay 실험 시 집계를 비우고 처음부터 재구축하기 위한 엔드포인트.

---

## 6. 데이터 모델

### order-service — `orders`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `order_id` | VARCHAR | PK |
| `user_id` | VARCHAR | |
| `product_name` | VARCHAR | |
| `quantity` | INT | |
| `unit_price` | BIGINT | |
| `total_price` | BIGINT | |
| `created_at` | TIMESTAMP | |

### stats-service — `product_stats`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `product_name` | VARCHAR | PK |
| `order_count` | INT | |
| `revenue` | BIGINT | |

### stats-service — `processed_event`  *(4단계에서 추가)*

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `order_id` | VARCHAR | **PK — 멱등성의 핵심** |
| `processed_at` | TIMESTAMP | |

> 이 테이블이 4단계의 답이다. replay로 같은 이벤트가 다시 와도 `order_id`가 이미 존재하면 집계를 건너뛴다.
> **단, 3단계에서는 이 테이블을 일부러 만들지 않는다.** 4단계에서 집계가 2배가 되는 문제를 먼저 겪어봐야 이 테이블의 필요성이 몸으로 이해되기 때문.

---

## 7. H2를 인메모리 모드로 쓰는 이유

`jdbc:h2:mem:...` (파일 모드 아님)

재기동하면 DB가 비워진다. 이것이 의도된 설계다.
- stats-service를 재기동하면 집계가 사라지고 → **Kafka 재생으로 다시 채워진다**
- "상태는 이벤트 로그로부터 재구축된다"는 이벤트 소싱의 기본 발상을 매 재기동마다 체험하게 됨

파일 모드로 두면 데이터가 남아서 이 그림이 흐려진다.

---

## 8. 포트 정리

| 대상 | 포트 |
|---|---|
| Kafka (브로커) | 9092 |
| Kafka UI | 8080 |
| order-service | 8081 |
| feed-service | 8082 |
| stats-service | 8083 |
| 프론트엔드 (Vite dev) | 5173 |

---

## 9. 모듈 구조

```
kafka_study/
├── docs/
├── docker/
│   └── docker-compose.yml          # Kafka(KRaft) + Kafka UI
├── backend/                        # Gradle 멀티모듈 (Kotlin DSL)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts            # 공통 플러그인/의존성
│   ├── common/                     # 이벤트 DTO 등 공유 모델
│   ├── order-service/
│   ├── feed-service/
│   └── stats-service/
└── frontend/                       # Vue 3 + Vite
```

`common` 모듈에 `OrderCreatedEvent`를 두어 세 서비스가 같은 스키마를 공유한다.

> 실무에서는 서비스별로 스키마를 복제하거나 Schema Registry로 관리하는 게 일반적이다. 학습 프로젝트에서는 공유 모듈이 단순하고, 6단계 이후 "스키마가 바뀌면 어떻게 되는가"를 다룰 때 오히려 좋은 논의 지점이 된다.
