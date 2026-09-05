# KS-4: Gradle 멀티모듈 뼈대 + 공통 이벤트 모델

| | |
|---|---|
| **타입** | CHORE |
| **상태** | DONE |
| **단계** | 1단계 — Producer |
| **브랜치** | `chore/KS-4-gradle-multimodule` |
| **PR** | [#14](https://github.com/ytw9699/kafka_study/pull/14) |

## 배경

세 서비스가 같은 이벤트 스키마를 공유해야 한다. 코드를 쓰기 전에 빌드 구조를 먼저 잡는다.
특히 **Kotlin + JPA 플러그인 설정**은 나중에 하면 엔티티를 전부 고쳐야 하므로 지금 잡는다.

## 목표

`./gradlew build`가 통과하는 멀티모듈 뼈대와, 세 서비스가 공유할 원장 이벤트 두 종류를 만든다.

## 작업 내용

- [x] `backend/settings.gradle.kts` — 4개 모듈 등록
- [x] 루트 `build.gradle.kts` — 공통 플러그인/의존성, JDK 17 toolchain 고정
- [x] `kotlin-spring`(allopen), `kotlin-jpa`(noarg) 플러그인 설정
- [x] `common` 모듈 — `PointEarnedEvent` / `PointSpentEvent` data class
- [x] `eventType` 기반 다형 역직렬화 설정 (Jackson `@JsonSubTypes`)
- [x] `ledger-api` / `balance-worker` / `fraud-service` 빈 모듈 + 기동 확인
- [x] Jackson `JavaTimeModule` 설정 (`Instant` 직렬화)

## 상세 설계

### 모듈 구조

```
backend/
├── settings.gradle.kts
├── build.gradle.kts       # 공통 플러그인/의존성
├── common/                # 이벤트 DTO
├── ledger-api/
├── balance-worker/
└── fraud-service/
```

### Kotlin + JPA 마찰 지점

- `kotlin-allopen` (`kotlin-spring` 프리셋) — `@Entity` 클래스를 자동 `open`으로
- `kotlin-jpa` (`kotlin-noarg` 프리셋) — JPA가 요구하는 기본 생성자 생성

**규칙: 엔티티는 `data class`로 만들지 않는다.** `equals`/`hashCode`가 lazy 프록시와 충돌한다.
`data class`는 `common`의 이벤트 DTO에만 쓴다.

### 이벤트 두 종류

`ledger-events` 토픽에 `POINT_EARNED`와 `POINT_SPENT`가 함께 흐른다.
컨슈머가 `eventType`으로 분기할 수 있어야 하므로 Jackson 다형 역직렬화를 설정한다.

**이벤트에 "잔액"을 담지 않는다.** 이벤트는 **일어난 사실**만 담고, 잔액은 이벤트를 순서대로 적용한 **결과**다.
발행 시점에 잔액을 계산해 담으면 발행자가 순서를 알아야 하므로 **병렬 처리가 불가능**해진다.

### `common` 공유의 한계

세 서비스가 같은 클래스를 쓰므로 **스키마 진화 학습이 구조적으로 불가능**하다.
이 프로젝트 범위 밖이므로 감수하되, 한계는 인지한다. (실무에서는 스키마 복제 또는 Schema Registry)

## 완료 조건 (DoD)

- [x] `./gradlew build` 성공
- [x] 세 서비스가 각각 기동된다 (빈 앱 상태)
- [x] `common`의 이벤트 두 종류를 세 모듈에서 참조 가능
- [x] JSON 문자열 → `eventType`에 따라 올바른 타입으로 역직렬화되는 테스트 통과
- [x] 테스트용 `@Entity` 클래스가 `open` 없이 컴파일된다

## 검증 결과

```
BUILD SUCCESSFUL in 1m 22s
23 actionable tasks: 23 executed
```

`common` 직렬화 테스트 5건 통과:

```
✅ 적립 이벤트가 왕복 직렬화된다
✅ 사용 이벤트가 왕복 직렬화된다
✅ eventType 만 보고 올바른 구현체로 역직렬화된다
✅ occurredAt 이 ISO-8601 문자열로 직렬화된다
✅ signedAmount 는 적립이 양수 사용이 음수다
```

세 서비스 기동 확인 (404는 정상 — 아직 엔드포인트가 없다):

```
ledger-api     (:8081) → HTTP 404 ✅
balance-worker (:8082) → HTTP 404 ✅
fraud-service  (:8083) → HTTP 404 ✅
```

## 배운 것

### `subprojects {}` 안에서는 `kotlin { }` 접근자를 쓸 수 없다

```kotlin
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    kotlin { jvmToolchain(17) }   // ❌ Unresolved reference
}
```

```
Line 30:     kotlin {
             ^ Unresolved reference. None of the following candidates is applicable
               because of receiver type mismatch
```

**타입 안전 접근자(type-safe accessor)는 스크립트 컴파일 시점에 생성**되는데,
`subprojects {}` 안의 `apply(plugin = ...)`는 **런타임에** 실행되므로 그 시점에 확장이 존재하지 않는다.

`plugins { }` 블록으로 적용한 플러그인만 접근자가 생긴다.

**해결**: 확장 타입을 명시한다.

```kotlin
extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(17) }
extensions.configure<DependencyManagementExtension> { imports { mavenBom(...) } }
```

같은 이유로 `dependencies { }` 안에서도 `implementation(...)` 대신 `"implementation"(...)` 문자열 형태를 쓴다.

### 이벤트에 "잔액"을 담지 않는 것이 병렬 처리의 전제다

처음엔 이벤트에 `balanceAfter` 같은 필드를 넣을까 고민했다. 넣으면 안 된다.

발행 시점에 잔액을 계산하려면 **발행자가 그 사용자의 이전 거래를 모두 알아야 한다.**
그러면 발행 자체가 순차 처리가 되어 이 프로젝트의 요구사항 2(병렬 처리)가 성립하지 않는다.

**이벤트는 일어난 사실만 담고, 잔액은 이벤트를 순서대로 적용한 파생 상태다.**
이것이 "Kafka 가 원장의 원본"이라는 설계의 실제 의미다.

### `@JsonTypeInfo`는 `EXISTING_PROPERTY`를 써야 한다

하나의 토픽에서 두 종류의 이벤트를 받으므로 다형 역직렬화가 필요하다.

기본값인 `As.PROPERTY`를 쓰면 Jackson 이 타입 판별용 프로퍼티를 **별도로 추가**해서
JSON 에 `eventType` 이 두 번 나타난다. `EXISTING_PROPERTY` + `visible = true` 로
**이미 있는 `eventType` 필드를 그대로 판별에 쓰도록** 해야 깔끔하다.

`visible = true` 가 없으면 역직렬화 시 `eventType` 필드가 채워지지 않는다.

### `JavaTimeModule` 없이는 `Instant` 직렬화 자체가 실패한다

그리고 `WRITE_DATES_AS_TIMESTAMPS` 를 끄지 않으면 epoch 숫자(`1757082730.123`)로 나간다.
Kafka UI 에서 사람이 읽을 수 있어야 하므로 ISO-8601 문자열로 두었다.

이 프로젝트에서 메시지를 눈으로 확인하는 일이 잦으므로 실질적인 차이가 있다.

### 로컬에 Gradle 이 없어도 wrapper 를 만들 수 있다

`gradle:8.10-jdk17` 이미지로 `gradle wrapper` 를 한 번만 실행하면 된다.

```bash
docker run --rm -v "$PWD":/w -w /w -u "$(id -u):$(id -g)" \
  -e GRADLE_USER_HOME=/w/.gradle-docker \
  gradle:8.10-jdk17 gradle wrapper --gradle-version 8.10
```

`-u`(현재 사용자)와 `GRADLE_USER_HOME`(캐시를 컨테이너 밖으로)을 주지 않으면
**root 소유 파일이 생겨 이후 빌드가 권한 오류로 실패한다.**

## 참고

- [02. 설계서 — 이벤트 스키마 / 모듈 구조](../planning/02-architecture.md)
- [03. 기술 스택 — Kotlin 선택 근거와 JPA 마찰](../planning/03-tech-stack.md)
