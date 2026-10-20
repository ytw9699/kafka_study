# 문서 인덱스

[← README](../README.md)

이 프로젝트는 Jira를 쓰지 않는다. **모든 기획·이슈·결정·트러블슈팅은 여기에 남긴다.**

### 처음이라면

| 시간이 있다면 | 읽을 것 |
|---|---|
| 3분 | [README](../README.md) — 무엇을 왜 만들었고 어떻게 돌려 보는지 |
| 5분 | [KS-20](issues/KS-20-order-broken-experiment.md) — 키 하나 바꿨더니 잔액이 틀어지는 하이라이트 |
| 10분 | [06. 진행 기록](planning/06-progress.md) — 무엇을 확인했고 무엇이 틀렸다가 고쳐졌나 |
| 10분 | [07. 메시지 큐](planning/07-message-queue.md) — 큐를 쓰는 이유와 대가 |

**티켓(`KS-N`) 문서는 "배경 → 한 일 → 결과 → 배운 것" 순서입니다.** 바쁘면 "배운 것"만 읽어도 됩니다.

## 규칙

| 문서 | 내용 |
|---|---|
| [문서 구조 규칙](conventions/01-doc-structure.md) | 어떤 문서를 어디에 쓰는가 |
| [티켓 체계](conventions/02-ticket.md) | `KS-N` 번호 발급, 티켓 양식, 진행 흐름 |
| [브랜치 / PR 전략](conventions/03-branch-strategy.md) | `main` / `dev` / `feature/*`, 커밋·PR 규칙 |

## 기획 · 설계

| 문서 | 내용 |
|---|---|
| [01. 기획서](planning/01-planning.md) | 왜 Kafka인가(정직한 주장 강도), 도메인, 요구사항 6개, 도구 비교, 실험 A~F |
| [02. 설계서](planning/02-architecture.md) | 전체 아키텍처, 흐름 시퀀스, 프론트/인프라 구조, 토픽·스키마·API |
| [03. 기술 스택 결정서](planning/03-tech-stack.md) | 스택 선택 근거와 탈락시킨 대안 |
| [04. 작업 로드맵](planning/04-roadmap.md) | 0~7단계 세부 작업 분해와 확인 방법 |
| [05. DB로 직접 만들면](planning/05-why-not-diy.md) | DIY 대응표, split-brain, fencing token |
| **[06. 진행 기록](planning/06-progress.md)** | **1차 완결 회고 — 복기용. 여기부터 읽어도 된다** |
| [07. 메시지 큐](planning/07-message-queue.md) | 출발점(Redis Pub/Sub), 큐를 쓰는 이유와 대가, 전달 보장, 재시도·DLQ, 분산 트랜잭션 |

---

## 티켓 보드

번호는 **착수 시점에 발급**한다. 앞으로 할 일의 전체 목록은 [작업 로드맵](planning/04-roadmap.md)에 있다.

### 준비

| 티켓 | 제목 | 타입 | 상태 |
|---|---|---|---|
| [KS-1](issues/KS-1-project-setup-docs.md) | 프로젝트 기획 및 운영 체계 수립 | DOCS | DONE |
| [KS-10](issues/KS-10-architecture-diagrams.md) | 전체 아키텍처 다이어그램 보강 | DOCS | DONE |
| [KS-11](issues/KS-11-design-finalize.md) | 설계 최종 확정 및 작업 분해 | DOCS | DONE |
| [KS-12](issues/KS-12-scenario-cancellation.md) | 시나리오 보강 — 주문 취소와 일자별 매출 | DOCS | DONE |
| [KS-13](issues/KS-13-pivot-ledger.md) | **도메인 전환 — 포인트 지갑 원장** | DOCS | DONE |
| [KS-24](issues/KS-24-progress-record.md) | 진행 기록 신설 및 문서-구현 불일치 정정 | DOCS | DONE |
| [KS-25](issues/KS-25-tool-comparison-fix.md) | 도구 비교표 정정 — "Kafka뿐"은 과장이었다 | DOCS | DONE |
| [KS-27](issues/KS-27-message-queue-concepts.md) | 메시지 큐 개념 문서 신설 및 문구 정리 | DOCS | DONE |
| [KS-28](issues/KS-28-readme-onboarding.md) | README 재작성 — 처음 보는 사람이 따라올 수 있게 | DOCS | DONE |
| [KS-30](issues/KS-30-readme-build-first.md) | README 실행 절차에 "먼저 빌드" 단계 추가 | DOCS | DONE |
| [KS-31](issues/KS-31-worker-status-and-pins.md) | 정상 종료한 워커가 "놀고 있음"으로 보이는 문제 수정, Kafka UI 버전 고정 | FIX | DONE |
| [KS-32](issues/KS-32-docs-navigation.md) | 문서 이동 링크 · 한눈 요약 추가, 낡은 설계 내용 갱신, README 에 기간 명시 | DOCS | DONE |
| [KS-33](issues/KS-33-readme-architecture-diagram.md) | README 맨 앞에 한눈에 보는 아키텍처 그림 추가 | DOCS | DONE |

### 단계별 (0~5단계 · 1차 완결)

| 티켓 | 제목 | 타입 | 단계 | 상태 |
|---|---|---|---|---|
| [KS-2](issues/KS-2-infra-docker-compose.md) | 로컬 Kafka 인프라 구성 | FEAT | 0단계 | DONE |
| [KS-3](issues/KS-3-kafka-cli-basics.md) | Kafka CLI 실습 — 그룹·파티션·재생 확인 | EXPERIMENT | 0단계 | DONE |
| [KS-4](issues/KS-4-gradle-multimodule.md) | Gradle 멀티모듈 뼈대 + 공통 이벤트 모델 | CHORE | 1단계 | DONE |
| [KS-5](issues/KS-5-postgres-setup.md) | PostgreSQL 추가 및 DB 분리 | FEAT | 1단계 | DONE |
| [KS-14](issues/KS-14-ledger-api-publish.md) | ledger-api — 거래 발행 및 SSE 게이트웨이 | FEAT | 2단계 | DONE |
| [KS-15](issues/KS-15-frontend-ledger-feed.md) | 프론트 뼈대 + 거래 폼 + 실시간 원장 피드 | FEAT | 2단계 | DONE |
| [KS-16](issues/KS-16-balance-worker.md) | balance-worker — 잔액 계산 컨슈머 | FEAT | 3단계 | DONE |
| [KS-17](issues/KS-17-balance-panel.md) | 잔액 화면 + 기대 잔액 대조 | FEAT | 3단계 | DONE |
| [KS-18](issues/KS-18-worker-restart-offset.md) | 워커 재기동 — offset 이어읽기 확인 | EXPERIMENT | 3단계 | DONE |
| [KS-19](issues/KS-19-worker-scale-out.md) | 워커 인스턴스 확장 + 파티션 배정 화면 | FEAT | 4단계 | DONE |
| [KS-20](issues/KS-20-order-broken-experiment.md) | ★ 순서가 깨지면 잔액이 틀어진다 (실험 A) | EXPERIMENT | 4단계 | DONE |
| [KS-21](issues/KS-21-rebalance-experiment.md) | 리밸런싱 관찰 (실험 B) | EXPERIMENT | 4단계 | DONE |
| [KS-22](issues/KS-22-replay-api.md) | 재생 API — rebuild / replay | FEAT | 5단계 | DONE |
| [KS-23](issues/KS-23-idempotency-experiment.md) | ★ 재생하면 두 배가 된다 → 멱등성 | EXPERIMENT | 5단계 | DONE |

**1차 목표(0~5단계) 완결.** 태그 `step-0` ~ `step-5`.

남은 **6~7단계(2차)** 는 [로드맵](planning/04-roadmap.md)에 분해되어 있으며, 착수 시점에 티켓으로 발급한다.
전체 회고는 [06. 진행 기록](planning/06-progress.md) 참조.

### 대기

| 티켓 | 제목 | 타입 | 상태 |
|---|---|---|---|
| [KS-29](issues/KS-29-throughput-experiment.md) | 처리량 실험 — 워커 · 파티션 · 배치가 처리 속도를 어떻게 바꾸는가 | EXPERIMENT | TODO |

### 취소됨

[KS-13](issues/KS-13-pivot-ledger.md)의 도메인 전환으로 폐기. **번호는 재사용하지 않는다.**

| 티켓 | 제목 |
|---|---|
| [KS-6](issues/KS-6-order-service-producer.md) | ~~order-service — 주문 생성·취소 및 이벤트 발행~~ |
| [KS-7](issues/KS-7-feed-service-sse.md) | ~~feed-service — 실시간 구독 및 SSE 중계~~ |
| [KS-8](issues/KS-8-frontend-scaffold.md) | ~~Vue 프로젝트 뼈대 + 주문 생성 폼~~ |
| [KS-9](issues/KS-9-order-feed-view.md) | ~~실시간 주문 피드 화면~~ |

## 트러블슈팅

| 문서 | 내용 |
|---|---|
| [TS-1](troubleshooting/TS-1-advertised-listeners.md) | KRaft 브로커가 `advertised.listeners ... 0.0.0.0` 로 기동 실패 |
| [TS-2](troubleshooting/TS-2-poison-pill.md) | 역직렬화 불가 메시지 하나가 컨슈머 전체를 멈춘다 (poison pill) |
| [TS-3](troubleshooting/TS-3-seek-callback.md) | 런타임 seek 이 컨슈머를 깨뜨린다 — 콜백 두 종류 구분 |

> Kafka는 설정 하나 때문에 안 되는 경우가 많다. 한참 붙잡은 문제는 반드시 `troubleshooting/`에 남긴다.
