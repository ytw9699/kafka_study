# Kafka Study — 주문 이벤트 스트림 토이 프로젝트

Kafka의 기초를 **단계적으로, 눈으로 확인하며** 익히기 위한 학습용 토이 프로젝트.

## 한 줄 요약

> 주문이 발생하면 실시간으로 화면에 뜬다.
> 그리고 **나중에 추가된 통계 서비스가 과거 주문까지 전부 읽어서** 매출 집계를 스스로 만들어낸다.

뒤쪽 문장이 이 프로젝트의 핵심이다. 큐(RabbitMQ/SQS/SNS)로는 구조적으로 불가능하고, Kafka여야만 되는 지점.

## 문서

**모든 기획·이슈·결정·트러블슈팅은 [`docs/`](docs/README.md)에 남긴다.** (Jira 대체)

| 문서 | 내용 |
|---|---|
| [문서 인덱스 · 티켓 보드](docs/README.md) | 전체 문서 목록과 `KS-N` 티켓 현황 |
| [01. 기획서](docs/planning/01-planning.md) | 왜 Kafka인가, 시나리오, 0~7단계 학습 로드맵 |
| [02. 설계서](docs/planning/02-architecture.md) | 시스템 구조, 토픽/파티션 설계, 이벤트 스키마, API 명세 |
| [03. 기술 스택 결정서](docs/planning/03-tech-stack.md) | 스택 선택 근거, 탈락한 대안들 |

작업 규칙: [문서 구조](docs/conventions/01-doc-structure.md) · [티켓 체계](docs/conventions/02-ticket.md) · [브랜치 전략](docs/conventions/03-branch-strategy.md)

## 기술 스택

Kotlin (JDK 17) · Spring Boot 3.x · Spring Kafka · Spring Data JPA + H2(인메모리) · Gradle 멀티모듈
Vue 3 + Vite · Pinia · Chart.js
Apache Kafka (KRaft) · Kafka UI · Docker Compose

## 사전 준비

- Docker / Docker Compose v2 이상
- JDK 17 이상
- Node.js 20 이상

## 진행 상황

| 단계 | 내용 | 티켓 | 상태 |
|---|---|---|---|
| - | 프로젝트 기획 및 운영 체계 수립 | [KS-1](docs/issues/KS-1-project-setup-docs.md) | ✅ DONE |
| 0단계 | 인프라 — Kafka(KRaft) + Kafka UI | [KS-2](docs/issues/KS-2-infra-docker-compose.md) | TODO |
| 1단계 | Producer — `order-service` | KS-3 | 예정 |
| 2단계 | 실시간 Consumer + 프론트 — `feed-service` | KS-4 | 예정 |
| 3단계 | **뒤늦게 합류하는 Consumer — `stats-service`** | KS-5 | 예정 |
| 4단계 | Replay & 멱등성 | KS-6 | 예정 |
| 5단계 | 파티션 & 리밸런싱 | KS-7 | 예정 |
| 6단계 | 에러 처리 & DLT | KS-8 | 예정 |
| 7단계 | Kubernetes 이관 (선택) | KS-9 | 예정 |

3단계가 이 프로젝트의 하이라이트다.

## 브랜치

| 브랜치 | 역할 |
|---|---|
| `main` | 각 학습 단계 완료 시점의 스냅샷 |
| `dev` | 기본 개발선 |
| `feature/KS-N-*` | 티켓 단위 작업 |
