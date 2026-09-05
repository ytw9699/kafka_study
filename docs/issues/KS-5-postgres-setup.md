# KS-5: PostgreSQL 추가 및 DB 분리

| | |
|---|---|
| **타입** | FEAT |
| **상태** | TODO |
| **단계** | 1단계 — Producer |
| **브랜치** | `feature/KS-5-postgres-setup` |
| **PR** | - |

## 배경

0단계에서는 Kafka만 띄웠다. 이제 상태를 저장할 DB가 필요하다.
**0단계에 넣지 않은 이유**: 컨테이너를 한꺼번에 늘리면 "Kafka가 안 뜬 건지 DB가 안 뜬 건지" 디버깅 표면이 넓어진다.

## 목표

컴포즈에 Postgres를 추가하고 서비스별 DB를 분리한다.

## 작업 내용

- [ ] `docker-compose.yml`에 `postgres` 서비스 추가 (:5432, named volume)
- [ ] `docker/init/01-create-databases.sql` — `orderdb`, `statsdb` 생성
- [ ] healthcheck 설정
- [ ] `psql`로 두 DB 접속 확인

## 상세 설계

### 왜 DB를 분리하는가

Postgres 인스턴스는 하나지만 `orderdb` / `statsdb`로 나눈다.
**MSA에서 서비스별 DB 분리는 기본 원칙**이고, 이것 자체가 학습 포인트다.
같은 DB를 쓰면 stats가 order 테이블을 직접 조회하는 지름길이 생겨, 이벤트로 소통한다는 전제가 흐려진다.

### 왜 H2가 아닌가

4단계 멱등성 구현의 핵심이 upsert인데 문법이 다르다.

| | 문법 |
|---|---|
| PostgreSQL | `INSERT ... ON CONFLICT ... DO UPDATE` |
| H2 | `MERGE INTO ... KEY(...)` |

가장 값진 학습 구간에서 H2 전용 문법을 익히면 실무 전이가 안 된다.
자세한 근거는 [03. 기술 스택 결정서](../planning/03-tech-stack.md) 참조.

## 완료 조건 (DoD)

- [ ] `docker compose up -d`로 Kafka + Kafka UI + Postgres가 모두 뜬다
- [ ] `psql`로 `orderdb`, `statsdb` 양쪽 접속 성공
- [ ] `docker compose down` → `up` 후 데이터 유지 (볼륨 확인)

## 배운 것

_(작업 후 작성)_

## 참고

- [02. 설계서 — 인프라 구성](../planning/02-architecture.md)
- [03. 기술 스택 — 영속성 결정 근거](../planning/03-tech-stack.md)
