# KS-2: 로컬 Kafka 인프라 구성

[← 문서 목록](../README.md) · [README](../../README.md)

| | |
|---|---|
| **타입** | FEAT |
| **상태** | DONE |
| **단계** | 0단계 — 인프라 |
| **브랜치** | `feature/KS-2-infra-docker-compose` |
| **PR** | [#10](https://github.com/ytw9699/kafka_study/pull/10) |

## 배경

애플리케이션 코드를 쓰기 전에 **Kafka 자체를 손으로 만져보는 단계**가 필요하다.
Spring 위에서 시작하면 `@KafkaListener`가 알아서 해주는 것들 때문에 토픽·파티션·offset이 무엇인지 감이 안 잡힌다. CLI로 직접 메시지를 넣고 빼보면서 실체를 먼저 본다.

## 목표

로컬에 Kafka 브로커와 Kafka UI를 띄우고, **CLI만으로 토픽 생성 → 발행 → 구독**을 해본다.
그 과정에서 파티션과 offset이 실제로 어떻게 동작하는지 눈으로 확인한다.

## 작업 내용

- [x] `docker/docker-compose.yml` 작성 — Kafka(KRaft 모드) + Kafka UI
- [x] 리스너 분리 설정 (`INTERNAL` / `EXTERNAL` / `CONTROLLER`)
- [x] Kafka 로그용 named volume 설정
- [x] 컨테이너 기동 및 Kafka UI 접속 확인 (`:8080`)
- [x] 호스트 CLI와 kafka-ui **양쪽 다** 브로커에 접속되는지 확인
- [x] `docs/planning/02-architecture.md`의 포트 표와 실제 구성 일치 확인

> CLI 실습(토픽 생성, produce/consume, 컨슈머 그룹, 재소비)은 [KS-3](KS-3-kafka-cli-basics.md)에서 다룬다.
> 이 티켓은 **인프라가 뜨는 것**까지가 범위다.

## 상세 설계

### 왜 KRaft 모드인가

- Zookeeper 없이 Kafka 단독 동작 → 컴포즈 구성이 단순해지고 컨테이너가 하나 줄어든다
- Kafka 3.3+ 부터 프로덕션 준비 완료, **Zookeeper 모드는 4.0에서 제거됐다.** 지금 배우는 게 앞으로의 표준

### 구성

| 컨테이너 | 포트 | 역할 |
|---|---|---|
| `kafka` | 9092 | 브로커 (KRaft, 단일 노드) |
| `kafka-ui` | **8090** | 토픽/메시지/컨슈머 그룹 조회 (컨테이너 내부 8080) |

**Postgres는 이 단계에서 띄우지 않는다.** [KS-5](KS-5-postgres-setup.md)에서 추가한다.
컨테이너를 한꺼번에 늘리면 "Kafka가 안 뜬 건지 DB가 안 뜬 건지" 디버깅 표면이 넓어진다.

### 주의 지점: `advertised.listeners`

Kafka 컨테이너를 띄울 때 가장 자주 막히는 부분.
브로커는 클라이언트에게 "나한테 접속하려면 이 주소로 오라"고 **광고(advertise)** 하는데, 이 주소가 잘못되면 연결은 되는 것 같다가 실제 produce/consume에서 실패한다.

- 컨테이너 **내부**에서 접속(kafka-ui → kafka): `kafka:29092`
- 호스트 **외부**에서 접속(Spring 앱, CLI): `localhost:9092`

두 리스너를 분리해서 설정해야 한다. 여기서 막히면 `troubleshooting/`에 기록한다.

### 볼륨 — 이 프로젝트에서 가장 중요한 인프라 조건

Kafka 로그를 named volume에 둔다. **`docker compose down` 후에도 토픽과 메시지가 유지되어야 한다.**
이것이 깨지면 5단계(원장 재구축)와 6단계(뒤늦게 합류한 컨슈머)가 성립하지 않는다.

## 완료 조건 (DoD)

- [x] `docker compose up -d` 한 번으로 Kafka + Kafka UI가 뜬다
- [x] 브라우저에서 `localhost:8090` 접속 시 브로커가 보인다 (`/api/clusters` → `status: online`)
- [x] 호스트에서 CLI로 브로커에 접속된다
- [x] `docker compose down` → `up` 후에도 토픽이 유지된다 ✅

> Kafka가 큐와 다르다는 것의 확인은 [KS-3](KS-3-kafka-cli-basics.md)에서 한다.

## 배운 것

### `advertised.listeners`에 CONTROLLER를 빠뜨리면 브로커가 아예 안 뜬다

커스텀 리스너를 쓰면서 `advertised.listeners`에 브로커용(INTERNAL/EXTERNAL)만 적었더니
`advertised.listeners cannot use the nonroutable meta-address 0.0.0.0` 로 기동 실패했다.

Kafka는 **`listeners`에는 있는데 `advertised.listeners`에 없는 리스너에 대해 `listeners` 값을 그대로 가져다 쓴다.**
그래서 `CONTROLLER://0.0.0.0:9093`이 되어 검증에 걸렸다.

→ [TS-1](../troubleshooting/TS-1-advertised-listeners.md)

### `listeners`와 `advertised.listeners`는 목적이 다르다

| | 의미 |
|---|---|
| `listeners` | **어디에 바인딩할 것인가** — `0.0.0.0`으로 모든 인터페이스에서 받음 |
| `advertised.listeners` | **클라이언트에게 뭐라고 알려줄 것인가** — 실제 도달 가능한 주소여야 함 |

클라이언트는 부트스트랩 서버에 붙어 **광고된 주소를 받아 다시 접속**한다.
그래서 광고 주소가 틀리면 "연결은 되는데 produce/consume에서 실패"하는 애매한 증상이 난다.

이번 건은 문법 검증에서 걸려 **기동 자체가 막힌 것**이라 오히려 원인을 빨리 찾을 수 있었다.

### 컨테이너 안의 `localhost`는 컨테이너 자신이다

`kafka-ui`가 `localhost:9092`로 붙으면 kafka-ui 컨테이너 자신에게 접속하려 한다.
그래서 컨테이너 간에는 `kafka:29092`(INTERNAL), 호스트에서는 `localhost:9092`(EXTERNAL)로 분리해야 한다.

**이것이 리스너를 두 개로 나누는 유일한 이유다.** 보안이나 성능 때문이 아니다.

### 볼륨 확인은 형식적인 절차가 아니었다

`docker compose down` → `up` 후 `ledger-events` 토픽이 그대로 남아 있는 것을 확인했다.
이것이 깨지면 5단계(원장 재구축)와 6단계(뒤늦게 합류한 컨슈머)가 **아예 성립하지 않는다.**
`down -v`는 전체 초기화이므로 학습 중에는 조심해서 쓴다.

### 호스트 포트 충돌은 흔하다

8080은 다른 앱이 쓰고 있어 Kafka UI를 **8090**으로 노출했다 (컨테이너 내부는 8080 그대로).
`ports: "8090:8080"` 처럼 호스트 포트만 바꾸면 컨테이너 설정은 건드릴 필요가 없다.

## 참고

- [02. 설계서 — 토픽 설계](../planning/02-architecture.md)
- [03. 기술 스택 — KRaft 모드 선택 근거](../planning/03-tech-stack.md)
