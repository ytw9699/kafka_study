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
| [01. 기획서](planning/01-planning.md) | 왜 Kafka인가, 시나리오, 0~7단계 학습 로드맵 |
| [02. 설계서](planning/02-architecture.md) | 시스템 구조, 토픽/파티션 설계, 이벤트 스키마, API 명세 |
| [03. 기술 스택 결정서](planning/03-tech-stack.md) | 스택 선택 근거와 탈락시킨 대안 |

---

## 티켓 보드

| 티켓 | 제목 | 타입 | 단계 | 상태 |
|---|---|---|---|---|
| [KS-1](issues/KS-1-project-setup-docs.md) | 프로젝트 기획 및 운영 체계 수립 | DOCS | - | IN REVIEW |
| [KS-2](issues/KS-2-infra-docker-compose.md) | 로컬 Kafka 인프라 구성 | FEAT | 0단계 | TODO |
| KS-3 | order-service — 주문 생성 및 이벤트 발행 | FEAT | 1단계 | 예정 |
| KS-4 | feed-service + 프론트 — 실시간 주문 피드 | FEAT | 2단계 | 예정 |
| KS-5 | stats-service — 과거 이벤트 재생으로 집계 구축 | FEAT | 3단계 | 예정 |
| KS-6 | offset 리셋 replay 및 멱등성 처리 | EXPERIMENT | 4단계 | 예정 |
| KS-7 | 파티션 분배와 리밸런싱 관찰 | EXPERIMENT | 5단계 | 예정 |
| KS-8 | 컨슈머 에러 처리 및 DLT 구성 | FEAT | 6단계 | 예정 |
| KS-9 | Kubernetes 이관 (선택) | FEAT | 7단계 | 예정 |

> **예정** 상태는 번호만 예약해둔 것. 작업을 시작할 때 `issues/`에 티켓 문서를 만들고 `TODO`로 바꾼다.
> 번호는 재사용하지 않는다.

## 트러블슈팅

| 문서 | 내용 |
|---|---|
| _(아직 없음)_ | |

> Kafka는 설정 하나 때문에 안 되는 경우가 많다. 한참 붙잡은 문제는 반드시 `troubleshooting/`에 남긴다.
