# 03. 기술 스택 결정서

[← README](../../README.md) · [문서 목록](../README.md)

> **이 문서 한눈에**
> - 어떤 기술을 썼는지 (Kotlin · Spring Kafka · PostgreSQL · Vue · KRaft)
> - **무엇을 왜 탈락시켰는지** — H2, Kubernetes, Avro, Kafka Streams 등
> - 쓰려다 결국 안 쓴 것(Chart.js)과 그 이유


선택한 스택과 **왜 그것을 골랐는지**, 그리고 **무엇을 탈락시켰는지** 기록한다.

## 최종 스택

| 영역 | 선택 |
|---|---|
| 언어 | Kotlin (JDK 17, Gradle toolchain 고정) |
| 프레임워크 | Spring Boot 3.x |
| 메시징 | Spring Kafka (`spring-kafka`) |
| 영속성 | Spring Data JPA + **PostgreSQL** (Docker, `balancedb` / `frauddb` 분리) |
| 빌드 | Gradle (Kotlin DSL), 멀티모듈 (`common` / `ledger-api` / `balance-worker` / `fraud-service`) |
| 프론트 | Vue 3 (Composition API) + Vite |
| 프론트 상태 | Pinia |
| 차트 | ~~Chart.js~~ — **결국 쓰지 않았다** (아래 참조) |
| 브로커 | Apache Kafka, KRaft 모드 |
| 모니터링 | Kafka UI |
| 로컬 인프라 | Docker Compose |
| 직렬화 | JSON |

---

## 결정 근거

### 언어: Kotlin

**선택 이유**
- Kafka 코드 자체(`KafkaTemplate`, `@KafkaListener`)는 Java와 거의 동일하게 생겼다. 쿠버네티스처럼 새로운 개념 레이어가 추가되는 게 아니라 **문법만 바뀌므로 Kafka 학습을 잡아먹지 않는다.**
- `sealed interface` 로 이벤트 타입(`POINT_EARNED` / `POINT_SPENT`)을 **닫힌 집합**으로 표현할 수 있다. `when` 분기에서 빠진 타입을 컴파일러가 잡아준다.
- Spring Boot의 Kotlin 지원이 공식적으로 1급이다 (전 스타터 호환 + Kotlin 전용 확장 함수 제공).

**알려진 마찰: Kotlin + JPA**
Kotlin은 클래스/프로퍼티가 기본 `final`인데 JPA는 프록시 생성을 위해 열려 있어야 한다. 다음 설정이 필요하다.

- `kotlin-allopen` (`kotlin-spring` 프리셋) — `@Entity` 클래스를 자동으로 `open`으로
- `kotlin-jpa` (`kotlin-noarg` 프리셋) — JPA가 요구하는 기본 생성자 자동 생성

**규칙**: **엔티티는 `data class`로 만들지 않는다.** `data class`가 생성하는 `equals`/`hashCode`가 lazy 프록시와 충돌한다. `data class`는 Kafka 메시지 DTO에만 사용한다.

> Kotlin으로 JPA를 쓰면 반드시 마주치는 지점이라, 처음부터 규칙으로 고정했다.

---

### 영속성: JPA + PostgreSQL

**왜 자바 컬렉션(`ConcurrentHashMap`)이 아니라 DB인가**

처음에는 "학습용이니 인메모리 Map으로 충분"이라 생각했지만, 두 가지 이유로 DB를 쓴다.

1. **현업 유사성** — 실제 컨슈머는 받은 이벤트를 DB에 쓴다. `@KafkaListener` 안에서 JPA 저장을 하는 코드가 실무 그 자체다.
2. **5단계 replay 실험이 성립한다** — DB에 잔액이 쌓인 상태에서 과거 이벤트를 다시 읽으면 **포인트가 두 배가 되는 문제**가 실제로 터진다. 자바 `Map`이면 키가 알아서 덮어써져서 이 문제가 **보이지 않고 지나가버린다.**

**왜 H2가 아니라 PostgreSQL인가**

H2 파일 모드도 이 프로젝트 규모에서는 굴러간다. 그럼에도 Postgres를 고른 이유:

1. **upsert 방언 차이 — 이 프로젝트에서 실제로 부딪힌다**
   잔액 갱신의 핵심이 "있으면 update, 없으면 insert"인데 문법이 다르다.

   | | 문법 |
   |---|---|
   | PostgreSQL | `INSERT ... ON CONFLICT (user_id) DO UPDATE SET ...` |
   | H2 | `MERGE INTO ... KEY(user_id) ...` |

   동시성 있는 upsert는 결국 네이티브 쿼리를 쓰게 된다. **가장 값진 학습 구간에서 H2 전용 문법을 익히면 실무 전이가 안 된다.**

2. **동시 접속 제한 — 이 프로젝트에서는 치명적이다** — H2 embedded 파일 모드는 한 JVM만 파일을 열 수 있다(file lock).
   **4단계에서 `balance-worker` 인스턴스를 3개 띄우는 것이 프로젝트의 핵심 실험인데, H2로는 아예 불가능하다.** 우회하려면 H2 server 모드를 띄워야 하는데, 그러면 Postgres 컨테이너 하나 띄우는 것과 수고가 같아진다.

3. **격리수준·락 동작 차이** — 동시 집계 갱신 시 경쟁 상태 처리가 다르다.

4. **"H2에선 되는데 운영에선 안 됨"** — 예약어, `GenerationType.IDENTITY` 동작, 타입 매핑에서 H2가 더 관대하다.

**비용**: 컨테이너 하나 추가. 이미 Kafka 컴포즈를 띄우고 있으므로 부담이 작다.
다만 디버깅 표면이 넓어지는 것을 줄이기 위해 **0단계는 Kafka만, Postgres는 1단계에서 추가**한다.

**왜 인메모리를 버렸는가**

한때 "H2 인메모리 + 기동 시마다 `seekToBeginning`"을 검토했다. 재기동할 때마다 상태가 비고 로그로부터 재구축되는 그림이 선명하기 때문이다. 그러나 **이것은 학습용으로 만든 인공적 구성이지 실무 패턴이 아니다.**

실무 컨슈머는 기동할 때마다 처음부터 읽지 않는다. 커밋된 offset부터 **이어서** 읽는다. 데이터가 수백만 건이면 매 기동마다 전체 재생은 애초에 불가능하다.
인메모리로 가면 "Kafka 컨슈머는 켤 때마다 전부 다시 읽는 것"이라는 **잘못된 감각이 남는다.**

대신 이렇게 한다.

| 상황 | 동작 | 실무성 |
|---|---|---|
| 평상시 | 커밋된 offset에서 **이어서** 읽기 | 실무 그대로 |
| 최초 기동 | `auto-offset-reset: earliest`로 전체 읽기 | 실무 그대로 (신규 컨슈머 투입) |
| 5단계 | `POST /api/balance/rebuild` — 잔액 비우고 처음부터 | 실무의 "전체 재구축" |
| 5단계 | `POST /api/balance/replay` — 잔액 **유지한 채** 처음부터 | 실무 사고 재현 |

**정상 동작과 재생을 구분해서 배우게 되므로 학습 효과가 오히려 더 좋다.**

**서비스별 저장소**

| 서비스 | DB | 이유 |
|---|---|---|
| ledger-api | **없음** | Kafka가 원장의 원본. 발행 + SSE 중계만 수행 |
| balance-worker | `balancedb` | 잔액 · 워커 배정 현황 · 멱등성 기록 |
| fraud-service | `frauddb` | 이상 징후 상태 |

Postgres 인스턴스는 하나지만 **DB를 분리**한다. MSA에서 서비스별 DB 분리는 기본 원칙이고, 이것도 학습 포인트다.

**`ledger-api`가 DB를 갖지 않는 것이 의도적이다.** 요청을 DB에 저장하면 "로그가 원본이고 나머지는 파생 상태"라는 이 프로젝트의 전제가 흐려진다.

### 브로커: Kafka KRaft 모드

**선택 이유**
- Zookeeper 없이 Kafka 단독으로 동작. 컴포즈 구성이 훨씬 단순하다.
- Kafka 3.3+ 부터 프로덕션 준비 완료로 선언됐고, **Zookeeper 모드는 4.0에서 제거됐다.** 즉 지금 배우는 게 앞으로의 표준이다.
- 학습 관점에서도 컨테이너가 하나 줄어드는 것이 부담을 낮춘다.

---

### 로컬 인프라: Docker Compose (쿠버네티스 제외)

**Kubernetes를 이 프로젝트에서 탈락시킨 이유**

1. **주인공이 바뀐다** — 쿠버로 가면 시간의 대부분이 Kafka가 아니라 쿠버 자체(StatefulSet, PVC, Headless Service, 브로커 `advertised.listeners` 설정)에 들어간다. Kafka를 쿠버에 제대로 올리는 건 브로커 주소 광고 문제 때문에 의외로 난이도가 높고, 실무에서는 Strimzi 오퍼레이터를 쓰는데 그러면 이번엔 Strimzi CRD를 배우게 된다.
2. **학습 리듬이 끊긴다** — 4~5단계 실험(컨슈머 인스턴스 껐다 켜기, 재생, 리밸런싱 관찰)은 **빠른 재기동 사이클**이 생명이다. 쿠버는 이미지 빌드 → 푸시 → 롤아웃 사이클이 매번 붙는다.

"Kafka 이해"와 "쿠버 운영 경험"은 별개 항목이므로 따로 쌓는 것이 이득이다. **이 프로젝트의 범위에서 완전히 제외한다.**

**Spring 서비스도 컨테이너에 넣지 않는다.** 같은 이유다 — 껐다 켜는 실험이 반복되는데 매번 이미지를 빌드하면 리듬이 끊긴다. IDE에서 바로 재기동할 수 있게 호스트에서 돌린다.

### 프론트: Vue 3 + Vite

**선택 이유**
- SSE 구독의 생명주기(연결/해제/재연결)와 **파티션 배정 현황 폴링**을 컴포넌트 단위로 분리할 수 있다.
- Composition API의 반응형 상태가 "이벤트가 흘러들어와 화면이 갱신된다"는 이 프로젝트의 성격과 잘 맞는다.
- Pinia로 **SSE로 받은 이벤트의 기대 잔액**과 **워커가 계산한 실제 잔액**을 각각 관리하면,
  4단계에서 **둘이 어긋나는 순간**을 자연스럽게 시각화할 수 있다. 이것이 이 프로젝트의 결정적 장면이다.

**전제**: 프론트는 어디까지나 **Kafka의 흐름을 눈으로 보는 창**이다. 화면은 최소로 유지하고, 프론트 작업이 Kafka 학습을 압도하지 않게 한다.

---

### 쓰지 않게 된 것 — Chart.js

처음에는 매출 추이 차트를 그릴 계획이라 넣었다. 도메인이 **포인트 원장**으로 바뀌면서
([KS-13](../issues/KS-13-pivot-ledger.md)) 시계열 차트가 필요 없어졌다.

지금 화면이 보여주는 것은 **표와 뱃지**다.

| 화면 | 형태 | 왜 |
|---|---|---|
| `LedgerFeed` | 목록 + **파티션 색상 뱃지** | 같은 색이 쌓이다 섞이는 패턴이 한눈에 보인다 |
| `BalancePanel` | **표** (워커 잔액 / 기대 잔액 / 차이) | 두 숫자의 차이가 핵심이므로 나란히 놓는 것이 최선 |
| `PartitionBoard` | 워커별 파티션 뱃지 | 배정이 옮겨가는 것을 보는 데 차트는 불필요 |

**차트가 표보다 나은 경우는 추세를 볼 때다.** 이 프로젝트는 "두 숫자가 같은가 다른가"를 보므로
표가 정확하고, 색상 뱃지가 패턴을 드러낸다.

> `package.json` 에는 의존성이 남아 있다. 6~7단계에서 쓸 여지가 있어 제거하지 않았다.

### 직렬화: JSON

**선택 이유**
기초 단계에서는 JSON이 디버깅에 압도적으로 유리하다. Kafka UI에서 메시지 내용을 바로 읽을 수 있고, CLI 콘솔 컨슈머로도 확인 가능하다.

**Avro + Schema Registry를 탈락시킨 이유**
스키마 진화(호환성 규칙, 컨슈머/프로듀서 버전 불일치)는 그 자체로 큰 주제다. 이걸 처음부터 얹으면 "왜 메시지가 안 읽히지"의 원인이 Kafka인지 스키마인지 구분이 안 된다. **심화 주제로 분리한다.**

---

## 탈락 항목 요약

| 탈락 | 대신 선택 | 이유 |
|---|---|---|
| Kubernetes | Docker Compose | Kafka 학습이 쿠버 학습에 묻힘. 프로젝트 범위에서 제외 |
| Java | Kotlin | Kafka 코드는 사실상 동일. `sealed interface` 로 이벤트 타입을 닫힌 집합으로 표현 |
| `ConcurrentHashMap` | JPA + DB | 멱등성 문제가 보이지 않고 지나감 |
| H2 (파일/인메모리) | PostgreSQL | **파일 모드는 한 JVM만 열 수 있어 워커 3개 실험이 불가능.** upsert 방언도 다름 |
| 인메모리 + 기동 시 `seekToBeginning` | 영속 DB + 명시적 재생 API | 실무 패턴이 아니고 "매번 전체를 읽는다"는 잘못된 감각을 남김 |
| Zookeeper 모드 | KRaft 모드 | Kafka 4.0에서 Zookeeper 제거됨 |
| Avro + Schema Registry | JSON | 디버깅 난이도 상승, 별도 심화 주제 |
| Kafka Streams | 일반 `@KafkaListener` | 진입장벽이 높고 핵심과 별개 |
| DB 큐 직접 구현 | Kafka | 학습 가치는 크지만 시간 비용이 큼. [05. 문서](05-why-not-diy.md)로 대신 |
| 바닐라 JS | Vue 3 | SSE 생명주기 + 실시간 차트 상태 관리 |
| Maven | Gradle (Kotlin DSL) | 빌드 스크립트도 같은 언어로 작성, 멀티모듈 구성이 간결 |

---

## 사전 준비

| 도구 | 버전 |
|---|---|
| Docker / Docker Compose | Compose v2 이상 |
| JDK | 17 이상 (Gradle toolchain으로 17 고정) |
| Node.js | 20 이상 |
