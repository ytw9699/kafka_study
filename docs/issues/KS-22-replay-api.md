# KS-22: 재생 API — rebuild / replay

| | |
|---|---|
| **타입** | FEAT |
| **상태** | TODO |
| **단계** | 5단계 |
| **브랜치** | `feature/KS-22-replay-api` |
| **PR** | - |

## 배경

[KS-18](KS-18-worker-restart-offset.md)에서 확인했듯 **재기동은 이어읽기**다.
`auto-offset-reset: earliest` 는 커밋된 offset 이 없을 때만 동작한다.

전체 재생을 하려면 **명시적으로 seek** 해야 한다. 이 티켓은 그 수단을 만든다.
실험은 [KS-23](KS-23-idempotency-experiment.md)에서 한다.

## 목표

워커를 살려둔 채 offset 을 처음으로 되감는 API 두 개를 만든다.

## 작업 내용

- [ ] `ConsumerSeekAware` 구현 — `registerSeekCallback` 에서 콜백 보관
- [ ] 배정받은 파티션 목록 추적
- [ ] `POST /api/balance/rebuild` — 잔액 **비우고** seek
- [ ] `POST /api/balance/replay` — 잔액 **유지한 채** seek
- [ ] `app.replay.delay-ms` 설정 (기본 0, 데모 시 50)
- [ ] 워커가 여러 개일 때 각자 자기 파티션만 seek 하는지 확인
- [ ] 프론트에 `rebuild` / `replay` 버튼 추가

## 상세 설계

### `KafkaConsumer` 는 thread-safe 하지 않다

REST 요청은 **HTTP 스레드**에서 오고, 컨슈머는 **리스너 스레드**에서 돈다.
REST 스레드에서 컨슈머를 직접 건드리면 안 된다.

`ConsumerSeekAware.registerSeekCallback` 에서 받은 `ConsumerSeekCallback` 을 보관해두고,
REST 요청 시 그것을 호출한다. **실제 seek 은 다음 poll 직전에 컨슈머 스레드에서 적용된다.**

### 워커가 여러 개면 각자 자기 파티션만 되감는다

`rebuild` 를 한 워커에 호출하면 **그 워커가 담당한 파티션만** 되감긴다.

이것을 어떻게 다룰지가 설계 판단이다.

| 방식 | 평가 |
|---|---|
| 각 워커에 개별 호출 | 단순하지만 사람이 전부 호출해야 함 |
| 워커를 1개로 줄이고 실행 | 실험 조건이 단순해짐 |
| 워커 간 조율 (Kafka 로 명령 전달 등) | 과도함. 이 프로젝트 범위 밖 |

**5단계 실험은 워커 1개로 진행한다.** 재생의 본질(offset seek)을 보는 것이 목적이고,
분산 조율은 다른 주제이기 때문이다. 이 제약을 문서에 명시한다.

### `rebuild` 와 `replay` 의 차이는 한 줄이다

```
rebuild : 잔액 테이블 비움 + seekToBeginning
replay  :                    seekToBeginning
```

**그 한 줄이 "정확히 복원"과 "두 배가 됨"을 가른다.**

### 재생 속도 조절

`app.replay.delay-ms` 를 주면 이벤트마다 잠깐 쉰다.
수십 건은 수백 ms 에 끝나 화면에서 과정이 보이지 않으므로, 데모 시 50ms 정도를 준다.

## 완료 조건 (DoD)

- [ ] `POST /api/balance/rebuild` 호출 시 잔액이 정확히 복원된다
- [ ] `POST /api/balance/replay` 호출 시 잔액이 **두 배가 된다**
- [ ] 재생 중 화면에서 잔액이 변해가는 것이 보인다
- [ ] 워커를 죽이지 않고 실행된다 (CLI `--reset-offsets` 와의 차이)

## 배운 것

_(작업 후 작성)_

## 참고

- [04. 작업 로드맵 — 5단계](../planning/04-roadmap.md)
- [KS-18 — 재기동은 이어읽기다](KS-18-worker-restart-offset.md)
