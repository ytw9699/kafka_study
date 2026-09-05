-- 서비스별 DB 분리.
--
-- Postgres 인스턴스는 하나지만 DB 를 나눈다.
-- MSA 에서 서비스별 DB 분리는 기본 원칙이고, 같은 DB 를 쓰면
-- fraud 가 balance 테이블을 직접 조회하는 지름길이 생겨
-- "이벤트로 소통한다"는 이 프로젝트의 전제가 흐려진다.
--
-- ledger-api 는 DB 를 갖지 않는다. Kafka 가 원장의 원본이다.

CREATE DATABASE balancedb;
CREATE DATABASE frauddb;
