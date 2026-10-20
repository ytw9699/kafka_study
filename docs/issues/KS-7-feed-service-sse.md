# KS-7: feed-service — 실시간 구독 및 SSE 중계

[← 문서 목록](../README.md) · [README](../../README.md)

| | |
|---|---|
| **타입** | FEAT |
| **상태** | **CANCELED** |
| **단계** | 2단계 — 실시간 Consumer + 프론트 |
| **브랜치** | `feature/KS-7-feed-service-sse` |
| **PR** | - |

> ## ⚠️ 이 티켓은 취소되었습니다 (CANCELED)
>
> [KS-13](KS-13-pivot-ledger.md)에서 도메인이 **주문/매출 → 포인트 지갑 원장**으로 전환되면서 폐기되었습니다.
> 기존 시나리오가 "왜 Kafka인가"를 증명하지 못한다는 것이 드러났기 때문입니다.
> 이 문서는 기록으로만 남깁니다. 새 티켓은 [보드](../README.md)를 참조하세요.
> 번호는 재사용하지 않습니다.



## 배경

이벤트 흐름을 **눈으로 보는 창**의 백엔드 절반. 첫 번째 컨슈머다.

## 목표

`order-events`를 실시간 구독해 SSE로 브라우저에 중계한다.

## 작업 내용

- [ ] `@KafkaListener` (`groupId = feed-group`, `auto-offset-reset: latest`)
- [ ] SSE 엔드포인트 `GET /api/feed/stream`
- [ ] `SseEmitter` 생명주기 관리 (연결 목록, 타임아웃, 에러 시 제거)
- [ ] 이벤트에 `partition` / `offset` 메타 포함해 전달
- [ ] 생성·취소 **두 종류** 이벤트를 모두 중계 (`eventType` 구분)
- [ ] `curl -N`으로 스트림 수신 확인

## 상세 설계

### 왜 `latest`인가

실시간 피드이므로 **기동 이후 들어온 주문만** 보면 된다.
`stats-service`(`earliest`)와의 대비가 3단계 하이라이트의 핵심이다.

### 상태를 갖지 않는다

DB도 메모리 버퍼도 두지 않는다. **`/api/feed/recent` 같은 조회 API를 만들지 않는다.**

SSE 연결 이전에 발생한 이벤트는 볼 수 없다. 이것은 결함이 아니라 SSE의 특성이며,
"과거를 읽는 것"은 Kafka를 구독하는 `stats-service`의 역할이다. **두 레이어를 섞지 않는다.**

### SseEmitter 주의

연결이 끊긴 emitter를 목록에서 제거하지 않으면 계속 쌓인다. `onCompletion` / `onTimeout` / `onError`에서 제거한다.

## 완료 조건 (DoD)

- [ ] `curl -N localhost:8082/api/feed/stream` 연결 유지
- [ ] 주문을 넣으면 **즉시** 스트림에 나타남
- [ ] 주문을 취소하면 취소 이벤트도 스트림에 나타남
- [ ] 전달 데이터에 `partition` / `offset` 포함
- [ ] 연결을 끊었다 다시 붙으면 **그 사이 이벤트는 보이지 않음** (SSE 특성 확인)
- [ ] Kafka UI에서 `feed-group`이 컨슈머 그룹 목록에 보임

## 배운 것

_(작업 후 작성)_

## 참고

- [04. 작업 로드맵 — 2단계](../planning/04-roadmap.md)
- [02. 설계서 — 서비스별 책임 / API 명세](../planning/02-architecture.md)
