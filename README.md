# Kafka Study — 주문 이벤트 스트림 토이 프로젝트

Kafka의 기초를 **단계적으로, 눈으로 확인하며** 익히기 위한 학습용 토이 프로젝트.

## 한 줄 요약

> 주문이 발생하면 실시간으로 화면에 뜬다.
> 그리고 **나중에 추가된 통계 서비스가 과거 주문까지 전부 읽어서** 매출 집계를 스스로 만들어낸다.

뒤쪽 문장이 이 프로젝트의 핵심이다. 큐(RabbitMQ/SQS/SNS)로는 불가능하고, Kafka여야만 되는 지점.

## 문서

| 문서 | 내용 |
|---|---|
| [01. 기획서](docs/01-planning.md) | 왜 Kafka인가, 시나리오, 단계별 학습 로드맵 |
| [02. 설계서](docs/02-architecture.md) | 시스템 구조, 토픽/파티션 설계, 이벤트 스키마, API 명세 |
| [03. 기술 스택 결정서](docs/03-tech-stack.md) | 스택 선택과 그 근거, 탈락한 대안들 |

## 사전 준비

- Docker / Docker Compose
- JDK 17 이상
- Node.js 20 이상

## 진행 상황

- [ ] 0단계 — 인프라 (Docker Compose: Kafka + Kafka UI)
- [ ] 1단계 — Producer (`order-service`)
- [ ] 2단계 — 실시간 Consumer + 프론트 (`feed-service` + Vue)
- [ ] 3단계 — 뒤늦게 합류하는 Consumer (`stats-service`) ← **하이라이트**
- [ ] 4단계 — Replay & 멱등성
- [ ] 5단계 — 파티션 & 리밸런싱
- [ ] 6단계 — 에러 처리 & DLT
- [ ] 7단계 (선택) — Kubernetes 이관
