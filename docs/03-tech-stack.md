# 03. 기술 스택 결정서

선택한 스택과 **왜 그것을 골랐는지**, 그리고 **무엇을 탈락시켰는지** 기록한다.

## 최종 스택

| 영역 | 선택 |
|---|---|
| 언어 | Kotlin (JDK 17, Gradle toolchain 고정) |
| 프레임워크 | Spring Boot 3.x |
| 메시징 | Spring Kafka (`spring-kafka`) |
| 영속성 | Spring Data JPA + H2 (인메모리) |
| 빌드 | Gradle (Kotlin DSL), 멀티모듈 |
| 프론트 | Vue 3 (Composition API) + Vite |
| 프론트 상태 | Pinia |
| 차트 | Chart.js |
| 브로커 | Apache Kafka, KRaft 모드 |
| 모니터링 | Kafka UI |
| 로컬 인프라 | Docker Compose |
| 직렬화 | JSON |

---

## 결정 근거

### 언어: Kotlin

**선택 이유**
- Kafka 코드 자체(`KafkaTemplate`, `@KafkaListener`)는 Java와 거의 동일하게 생겼다. 쿠버네티스처럼 새로운 개념 레이어가 추가되는 게 아니라 **문법만 바뀌므로 Kafka 학습을 잡아먹지 않는다.**
- 국내 백엔드 채용 시장에서 Kotlin + Spring Boot 조합이 실질적 가산점. 다수 기업이 Kotlin으로 전환했고 "Kotlin 경험 우대"가 흔하다.
- Spring Boot의 Kotlin 지원이 공식적으로 1급이다 (전 스타터 호환 + Kotlin 전용 확장 함수 제공).

**알려진 마찰: Kotlin + JPA**
Kotlin은 클래스/프로퍼티가 기본 `final`인데 JPA는 프록시 생성을 위해 열려 있어야 한다. 다음 설정이 필요하다.

- `kotlin-allopen` (`kotlin-spring` 프리셋) — `@Entity` 클래스를 자동으로 `open`으로
- `kotlin-jpa` (`kotlin-noarg` 프리셋) — JPA가 요구하는 기본 생성자 자동 생성

**규칙**: **엔티티는 `data class`로 만들지 않는다.** `data class`가 생성하는 `equals`/`hashCode`가 lazy 프록시와 충돌한다. `data class`는 Kafka 메시지 DTO에만 사용한다.

> 이 세팅 자체가 실무 자산이다. Kotlin+JPA를 제대로 구성해본 경험은 채용 과정에서도 자주 물어보는 지점.

---

### 영속성: JPA + H2 (인메모리)

**왜 자바 컬렉션(`ConcurrentHashMap`)이 아니라 DB인가**

처음에는 "학습용이니 인메모리 Map으로 충분"이라 생각했지만, 두 가지 이유로 DB를 쓴다.

1. **현업 유사성** — 실제 컨슈머는 받은 이벤트를 DB에 쓴다. `@KafkaListener` 안에서 JPA 저장을 하는 코드가 실무 그 자체다.
2. **4단계 replay 실험이 성립한다** — DB에 집계가 쌓인 상태에서 offset을 리셋하면 **같은 주문이 두 번 집계되는 문제**가 실제로 터진다. 이게 Kafka 실무의 핵심 주제인 **멱등성**이고, `orderId`를 PK로 잡아 중복을 막는 식으로 해결하게 된다.
   자바 `Map`이면 키가 알아서 덮어써져서 이 문제가 **보이지 않고 지나가버린다.** 학습 기회를 잃는다.

**왜 파일 모드가 아니라 인메모리 모드인가**

`jdbc:h2:file:`로 두면 재기동해도 데이터가 남아서, "**Kafka 재생으로 상태를 재구축한다**"는 3단계의 그림이 흐려진다.
인메모리 모드는 재기동 시 DB가 비고 → Kafka에서 다시 채워진다. 이벤트 소싱의 기본 발상을 매 재기동마다 체험하게 된다.

**서비스별 저장소**

| 서비스 | 저장소 | 이유 |
|---|---|---|
| order-service | H2 | 주문 원본 저장 후 이벤트 발행 (실무 패턴) |
| feed-service | 없음 (stateless) | Kafka → SSE 릴레이만 수행 |
| stats-service | H2 | replay/멱등성 실험의 무대 |

---

### 브로커: Kafka KRaft 모드

**선택 이유**
- Zookeeper 없이 Kafka 단독으로 동작. 컴포즈 구성이 훨씬 단순하다.
- Kafka 3.3+ 부터 프로덕션 준비 완료로 선언됐고, **Zookeeper 모드는 4.0에서 제거됐다.** 즉 지금 배우는 게 앞으로의 표준이다.
- 학습 관점에서도 컨테이너가 하나 줄어드는 것이 부담을 낮춘다.

---

### 로컬 인프라: Docker Compose (쿠버네티스 아님)

**Kubernetes를 0단계에서 탈락시킨 이유**

1. **주인공이 바뀐다** — 쿠버로 가면 초반 시간의 대부분이 Kafka가 아니라 쿠버 자체(StatefulSet, PVC, Headless Service, 브로커 `advertised.listeners` 설정)에 들어간다. Kafka를 쿠버에 제대로 올리는 건 브로커 주소 광고 문제 때문에 의외로 난이도가 높고, 실무에서는 Strimzi 오퍼레이터를 쓰는데 그러면 이번엔 Strimzi CRD를 배우게 된다.
2. **학습 리듬이 끊긴다** — 3~5단계 실험(컨슈머 인스턴스 껐다 켜기, offset 리셋, 리밸런싱 관찰)은 **빠른 재기동 사이클**이 생명이다. 쿠버는 이미지 빌드 → 푸시 → 롤아웃 사이클이 매번 붙는다.

**대신**: 7단계(선택)에서 **이미 동작하는 앱을 쿠버로 이관**한다. 그때는 순수하게 쿠버/Strimzi만 배우게 되어 학습이 섞이지 않는다. 이직 관점에서도 "Kafka 이해"와 "쿠버 운영 경험"은 별개 항목이므로 따로 쌓는 것이 이득이다.

---

### 프론트: Vue 3 + Vite

**선택 이유**
- SSE 구독의 생명주기(연결/해제/재연결)와 실시간 차트 갱신을 컴포넌트 단위로 깔끔하게 분리할 수 있다.
- Composition API의 반응형 상태가 "이벤트가 흘러들어와 화면이 갱신된다"는 이 프로젝트의 성격과 잘 맞는다.
- Pinia로 주문 피드/집계 상태를 전역 관리하면, 3단계에서 **집계가 재생되며 차트가 채워지는 과정**을 자연스럽게 시각화할 수 있다.

**전제**: 프론트는 어디까지나 **Kafka의 흐름을 눈으로 보는 창**이다. 화면은 최소로 유지하고, 프론트 작업이 Kafka 학습을 압도하지 않게 한다.

---

### 직렬화: JSON

**선택 이유**
기초 단계에서는 JSON이 디버깅에 압도적으로 유리하다. Kafka UI에서 메시지 내용을 바로 읽을 수 있고, CLI 콘솔 컨슈머로도 확인 가능하다.

**Avro + Schema Registry를 탈락시킨 이유**
스키마 진화(호환성 규칙, 컨슈머/프로듀서 버전 불일치)는 그 자체로 큰 주제다. 이걸 처음부터 얹으면 "왜 메시지가 안 읽히지"의 원인이 Kafka인지 스키마인지 구분이 안 된다. **심화 주제로 분리한다.**

---

## 탈락 항목 요약

| 탈락 | 대신 선택 | 이유 |
|---|---|---|
| Kubernetes (0단계) | Docker Compose | Kafka 학습이 쿠버 학습에 묻힘. 7단계로 이관 |
| Java | Kotlin | 채용 시장 가산점, Kafka 코드는 사실상 동일 |
| `ConcurrentHashMap` | JPA + H2 | 멱등성 문제가 보이지 않고 지나감 |
| H2 파일 모드 | H2 인메모리 모드 | "재생으로 상태 재구축"이 흐려짐 |
| Zookeeper 모드 | KRaft 모드 | Kafka 4.0에서 Zookeeper 제거됨 |
| Avro + Schema Registry | JSON | 디버깅 난이도 상승, 별도 심화 주제 |
| Kafka Streams | 일반 `@KafkaListener` | 진입장벽이 높고 핵심(로그 재생)과 별개 |
| 바닐라 JS | Vue 3 | SSE 생명주기 + 실시간 차트 상태 관리 |
| Maven | Gradle (Kotlin DSL) | 최근 채용공고 선호도, Kotlin과의 정합성 |

---

## 사전 준비

| 도구 | 버전 |
|---|---|
| Docker / Docker Compose | Compose v2 이상 |
| JDK | 17 이상 (Gradle toolchain으로 17 고정) |
| Node.js | 20 이상 |
