# 문서 인덱스

이 프로젝트는 Jira를 쓰지 않는다. **모든 기획·이슈·결정·트러블슈팅은 여기에 남긴다.**

## 규칙

| 문서 | 내용 |
|---|---|
| [문서 구조 규칙](conventions/01-doc-structure.md) | 어떤 문서를 어디에 쓰는가 |
| [티켓 체계](conventions/02-ticket.md) | `KS-N` 번호 발급, 티켓 양식, 진행 흐름 |
| [브랜치 / PR 전략](conventions/03-branch-strategy.md) | `main` / `dev` / `feature/*`, 커밋·PR 규칙 |

## 기획 · 설계

| 문서 | 내용 |
|---|---|
| [01. 기획서](planning/01-planning.md) | 왜 Kafka인가, 시나리오, 0~6단계 학습 로드맵 |
| [02. 설계서](planning/02-architecture.md) | 전체 아키텍처, 흐름 시퀀스, 프론트/인프라 구조, 토픽·스키마·API 명세 |
| [03. 기술 스택 결정서](planning/03-tech-stack.md) | 스택 선택 근거와 탈락시킨 대안 |
| [04. 작업 로드맵](planning/04-roadmap.md) | 단계별 세부 작업 분해와 확인 방법 |

---

## 티켓 보드

번호는 **착수 시점에 발급**한다. 앞으로 할 일의 전체 목록은 [작업 로드맵](planning/04-roadmap.md)에 있다.

| 티켓 | 제목 | 타입 | 단계 | 상태 |
|---|---|---|---|---|
| [KS-1](issues/KS-1-project-setup-docs.md) | 프로젝트 기획 및 운영 체계 수립 | DOCS | - | DONE |
| [KS-10](issues/KS-10-architecture-diagrams.md) | 전체 아키텍처 다이어그램 보강 | DOCS | - | DONE |
| [KS-11](issues/KS-11-design-finalize.md) | 설계 최종 확정 및 작업 분해 | DOCS | - | DONE |
| [KS-12](issues/KS-12-scenario-cancellation.md) | 시나리오 보강 — 주문 취소와 일자별 매출 | DOCS | - | DONE |
| [KS-2](issues/KS-2-infra-docker-compose.md) | 로컬 Kafka 인프라 구성 | FEAT | 0단계 | TODO |
| [KS-3](issues/KS-3-kafka-cli-basics.md) | Kafka CLI 실습 — 큐와 다른 지점 확인 | EXPERIMENT | 0단계 | TODO |
| [KS-4](issues/KS-4-gradle-multimodule.md) | Gradle 멀티모듈 뼈대 + 공통 이벤트 모델 | CHORE | 1단계 | TODO |
| [KS-5](issues/KS-5-postgres-setup.md) | PostgreSQL 추가 및 DB 분리 | FEAT | 1단계 | TODO |
| [KS-6](issues/KS-6-order-service-producer.md) | order-service — 주문 생성·취소 및 이벤트 발행 | FEAT | 1단계 | TODO |
| [KS-7](issues/KS-7-feed-service-sse.md) | feed-service — 실시간 구독 및 SSE 중계 | FEAT | 2단계 | TODO |
| [KS-8](issues/KS-8-frontend-scaffold.md) | Vue 프로젝트 뼈대 + 주문 생성 폼 | FEAT | 2단계 | TODO |
| [KS-9](issues/KS-9-order-feed-view.md) | 실시간 주문 피드 화면 | FEAT | 2단계 | TODO |

3~6단계 작업은 [로드맵](planning/04-roadmap.md)에 분해되어 있으며, 2단계 완료 후 티켓으로 발급한다.

## 트러블슈팅

| 문서 | 내용 |
|---|---|
| _(아직 없음)_ | |

> Kafka는 설정 하나 때문에 안 되는 경우가 많다. 한참 붙잡은 문제는 반드시 `troubleshooting/`에 남긴다.
