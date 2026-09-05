# KS-4: Gradle 멀티모듈 뼈대 + 공통 이벤트 모델

| | |
|---|---|
| **타입** | CHORE |
| **상태** | TODO |
| **단계** | 1단계 — Producer |
| **브랜치** | `chore/KS-4-gradle-multimodule` |
| **PR** | - |

## 배경

세 서비스가 같은 이벤트 스키마를 공유해야 한다. 코드를 쓰기 전에 빌드 구조를 먼저 잡는다.
특히 **Kotlin + JPA 플러그인 설정**은 나중에 하면 엔티티를 전부 고쳐야 하므로 지금 잡는다.

## 목표

`./gradlew build`가 통과하는 멀티모듈 뼈대와, 세 서비스가 공유할 `OrderCreatedEvent`를 만든다.

## 작업 내용

- [ ] `backend/settings.gradle.kts` — 4개 모듈 등록
- [ ] 루트 `build.gradle.kts` — 공통 플러그인/의존성, JDK 17 toolchain 고정
- [ ] `kotlin-spring`(allopen), `kotlin-jpa`(noarg) 플러그인 설정
- [ ] `common` 모듈 — `OrderCreatedEvent` / `OrderCanceledEvent` data class
- [ ] `eventType` 기반 다형 역직렬화 설정 (Jackson `@JsonSubTypes`)
- [ ] `order-service` / `feed-service` / `stats-service` 빈 모듈 + 기동 확인
- [ ] Jackson `JavaTimeModule` 설정 (`Instant` 직렬화)

## 상세 설계

### 모듈 구조

```
backend/
├── settings.gradle.kts
├── build.gradle.kts       # 공통 플러그인/의존성
├── common/                # 이벤트 DTO
├── order-service/
├── feed-service/
└── stats-service/
```

### Kotlin + JPA 마찰 지점

- `kotlin-allopen` (`kotlin-spring` 프리셋) — `@Entity` 클래스를 자동 `open`으로
- `kotlin-jpa` (`kotlin-noarg` 프리셋) — JPA가 요구하는 기본 생성자 생성

**규칙: 엔티티는 `data class`로 만들지 않는다.** `equals`/`hashCode`가 lazy 프록시와 충돌한다.
`data class`는 `common`의 이벤트 DTO에만 쓴다.

### 이벤트 두 종류

`order-events` 토픽에 `ORDER_CREATED`와 `ORDER_CANCELED`가 함께 흐른다.
컨슈머가 `eventType`으로 분기할 수 있어야 하므로 Jackson 다형 역직렬화를 설정한다.

**취소 이벤트에도 `productName`, `totalPrice`를 중복해서 담는다.**
컨슈머가 "그 주문이 얼마짜리였는지"를 알려고 원본 DB를 조회해야 한다면 서비스 경계가 무너진다.
**이벤트는 자기 완결적이어야 한다.**

### `common` 공유의 한계

세 서비스가 같은 클래스를 쓰므로 **스키마 진화 학습이 구조적으로 불가능**하다.
이 프로젝트 범위 밖이므로 감수하되, 한계는 인지한다. (실무에서는 스키마 복제 또는 Schema Registry)

## 완료 조건 (DoD)

- [ ] `./gradlew build` 성공
- [ ] 세 서비스가 각각 기동된다 (빈 앱 상태)
- [ ] `common`의 이벤트 두 종류를 세 모듈에서 참조 가능
- [ ] JSON 문자열 → `eventType`에 따라 올바른 타입으로 역직렬화되는 테스트 통과
- [ ] 테스트용 `@Entity` 클래스가 `open` 없이 컴파일된다

## 배운 것

_(작업 후 작성)_

## 참고

- [02. 설계서 — 이벤트 스키마 / 모듈 구조](../planning/02-architecture.md)
- [03. 기술 스택 — Kotlin 선택 근거와 JPA 마찰](../planning/03-tech-stack.md)
