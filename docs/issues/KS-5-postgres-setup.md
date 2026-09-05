# KS-5: PostgreSQL 추가 및 DB 분리

| | |
|---|---|
| **타입** | FEAT |
| **상태** | IN REVIEW |
| **단계** | 1단계 — Producer |
| **브랜치** | `feature/KS-5-postgres-setup` |
| **PR** | [#15](https://github.com/ytw9699/kafka_study/pull/15) |

## 배경

0단계에서는 Kafka만 띄웠다. 이제 상태를 저장할 DB가 필요하다.
**0단계에 넣지 않은 이유**: 컨테이너를 한꺼번에 늘리면 "Kafka가 안 뜬 건지 DB가 안 뜬 건지" 디버깅 표면이 넓어진다.

## 목표

컴포즈에 Postgres를 추가하고 서비스별 DB를 분리한다.

## 작업 내용

- [x] `docker-compose.yml`에 `postgres` 서비스 추가 (:5432, named volume)
- [x] `docker/init/01-create-databases.sql` — `balancedb`, `frauddb` 생성
- [x] healthcheck 설정
- [x] `psql`로 두 DB 접속 확인

## 상세 설계

### 왜 DB를 분리하는가

Postgres 인스턴스는 하나지만 `balancedb` / `frauddb`로 나눈다.
**MSA에서 서비스별 DB 분리는 기본 원칙**이고, 이것 자체가 학습 포인트다.
같은 DB를 쓰면 fraud가 balance 테이블을 직접 조회하는 지름길이 생겨, 이벤트로 소통한다는 전제가 흐려진다.

### 왜 H2가 아닌가

**H2 embedded 파일 모드는 한 JVM만 파일을 열 수 있다 (file lock).**
4단계에서 `balance-worker` 인스턴스를 3개 띄우는 것이 이 프로젝트의 핵심 실험인데, **H2로는 아예 불가능하다.**

추가로 upsert 문법도 다르다.

| | 문법 |
|---|---|
| PostgreSQL | `INSERT ... ON CONFLICT ... DO UPDATE` |
| H2 | `MERGE INTO ... KEY(...)` |
자세한 근거는 [03. 기술 스택 결정서](../planning/03-tech-stack.md) 참조.

## 완료 조건 (DoD)

- [x] `docker compose up -d`로 Kafka + Kafka UI + Postgres가 모두 뜬다
- [x] `psql`로 `balancedb`, `frauddb` 양쪽 접속 성공
- [x] `docker compose down` → `up` 후 데이터 유지 (볼륨 확인)

## 검증 결과

```
$ docker compose -f docker/docker-compose.yml ps
NAME       STATUS
kafka      Up (healthy)
kafka-ui   Up
postgres   Up (healthy)
```

DB 분리 확인:

```
   Name    |   Owner    | Encoding
 balancedb | kafkastudy | UTF8
 frauddb   | kafkastudy | UTF8

balancedb: balancedb
frauddb:   frauddb
```

## 배운 것

### init 스크립트는 볼륨이 비어 있을 때 **최초 1회만** 실행된다

`/docker-entrypoint-initdb.d` 의 `.sql` 은 Postgres 가 **데이터 디렉토리를 새로 초기화할 때만** 돈다.
이미 데이터가 있는 볼륨이면 컨테이너를 재기동해도 **다시 실행되지 않는다.**

→ 스크립트를 고쳤는데 반영이 안 되면 `docker compose down -v` 로 볼륨을 지워야 한다.
   운영에서는 마이그레이션 도구(Flyway/Liquibase)를 쓰는 이유이기도 하다.

### `ledger-api` 만 DB 가 없다 — 의도적이다

| 서비스 | DB |
|---|---|
| `ledger-api` | **없음** |
| `balance-worker` | `balancedb` |
| `fraud-service` | `frauddb` |

`ledger-api` 는 요청을 받아 Kafka 에 발행만 한다. **Kafka 가 원장의 원본(source of truth)이다.**
요청을 DB 에도 저장하면 "로그가 원본이고 나머지는 파생 상태"라는 전제가 흐려진다.

### DB 를 나누는 것은 형식이 아니라 지름길을 막는 장치다

인스턴스는 하나지만 `balancedb` / `frauddb` 로 나눴다.

같은 DB 를 쓰면 `fraud-service` 가 `user_balance` 테이블을 **직접 조회하는 지름길**이 생긴다.
그러면 "이벤트로 소통한다"는 전제가 무너지고, 6단계(나중에 합류해 과거를 재생)의 의미도 사라진다.

**DB 분리는 그 유혹을 구조적으로 막는다.**

### Postgres 를 0단계가 아니라 1단계에 넣은 판단은 옳았다

0단계에서 Kafka 만 띄웠기 때문에 `advertised.listeners` 문제([TS-1](../troubleshooting/TS-1-advertised-listeners.md))가 났을 때
**원인이 Kafka 하나로 좁혀져 있었다.** 컨테이너가 셋이었다면 디버깅 표면이 훨씬 넓었을 것이다.

### healthcheck 는 `depends_on` 과 함께 써야 의미가 있다

`pg_isready` 만 걸어두는 것으로는 부족하고, 의존하는 쪽에서 `condition: service_healthy` 를 명시해야
**"떴지만 아직 연결을 못 받는" 상태**에서 접속을 시도하지 않는다.
kafka-ui 가 kafka 에 대해 이 방식을 쓰고 있다.

## 참고

- [02. 설계서 — 인프라 구성](../planning/02-architecture.md)
- [03. 기술 스택 — 영속성 결정 근거](../planning/03-tech-stack.md)
