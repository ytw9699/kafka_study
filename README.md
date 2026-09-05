# Kafka Study — 주문 이벤트 스트림 토이 프로젝트

Kafka의 기초를 **단계적으로, 눈으로 확인하며** 익히기 위한 학습용 토이 프로젝트.

## 한 줄 요약

> 주문이 생성되거나 취소되면 실시간으로 화면에 뜬다.
> 그리고 **나중에 추가된 통계 서비스가 과거 이벤트를 전부 읽어서 일자별 매출을 스스로 만들어낸다.**
> 3월에 생성되고 4월에 취소된 주문은 **3월 매출에 그대로 남고 4월에만 차감된다.**

마지막 문장이 이 프로젝트의 급소다.
`orders` 테이블은 `status = CANCELED`만 알 뿐 **언제 취소됐는지 모른다.** DB 조회로는 재현할 수 없다.

> **"DB는 지금을 알고, 이벤트 로그는 과거를 안다."**

"그냥 DB에 넣고 조회하면 되지 않나?"에 대한 답은 [기획서 5절](docs/planning/01-planning.md)에 있다.

## 문서

**모든 기획·이슈·결정·트러블슈팅은 [`docs/`](docs/README.md)에 남긴다.** (Jira 대체)

| 문서 | 내용 |
|---|---|
| [문서 인덱스 · 티켓 보드](docs/README.md) | 전체 문서 목록과 `KS-N` 티켓 현황 |
| [01. 기획서](docs/planning/01-planning.md) | 왜 Kafka인가, 시나리오, 학습 로드맵 |
| [02. 설계서](docs/planning/02-architecture.md) | 전체 아키텍처, 흐름 시퀀스, 프론트/인프라 구조, 토픽·스키마·API |
| [03. 기술 스택 결정서](docs/planning/03-tech-stack.md) | 스택 선택 근거, 탈락한 대안들 |
| [04. 작업 로드맵](docs/planning/04-roadmap.md) | 단계별 세부 작업 분해 |

작업 규칙: [문서 구조](docs/conventions/01-doc-structure.md) · [티켓 체계](docs/conventions/02-ticket.md) · [브랜치 전략](docs/conventions/03-branch-strategy.md)

## 기술 스택

Kotlin (JDK 17) · Spring Boot 3.x · Spring Kafka · Spring Data JPA + PostgreSQL · Gradle 멀티모듈
Vue 3 + Vite · Pinia · Chart.js
Apache Kafka (KRaft) · Kafka UI · Docker Compose

## 사전 준비

- Docker / Docker Compose v2 이상
- JDK 17 이상
- Node.js 20 이상

## 진행 상황

**1차 목표는 4단계까지.** 여기까지만 해도 "왜 Kafka인가"에 완전히 답할 수 있다.

| 구간 | 단계 | 내용 | 티켓 |
|---|---|---|---|
| 준비 | - | 기획 · 설계 · 운영 체계 | KS-1, KS-10, KS-11 ✅ |
| **1차** | 0단계 | 인프라 — Kafka(KRaft) + Kafka UI, CLI 실습 | KS-2, KS-3 |
| **1차** | 1단계 | Producer — `order-service`(생성·취소), PostgreSQL | KS-4 ~ KS-6 |
| **1차** | 2단계 | 실시간 Consumer + 프론트 — `feed-service`, Vue | KS-7 ~ KS-9 |
| **1차** | 3단계 ★ | **뒤늦게 합류하는 Consumer — 일자별 매출 재구축** | 미발급 |
| **1차** | 4단계 | Replay & 멱등성 | 미발급 |
| 2차 | 5단계 | 파티션 & 리밸런싱 (+ SSE의 한계 확인) | 미발급 |
| 2차 | 6단계 | 에러 처리 & DLT | 미발급 |

**3단계가 이 프로젝트의 목적지다.** 0~2단계는 3단계를 성립시키기 위한 준비다.
4단계 완료 시 `main` 머지 + `step-4` 태그로 1차 완결.

## 브랜치

| 브랜치 | 역할 |
|---|---|
| `main` | 각 학습 단계 완료 시점의 스냅샷 |
| `dev` | 기본 개발선 |
| `feature/KS-N-*` | 티켓 단위 작업 |
