# TS-2: 역직렬화 불가 메시지 하나가 컨슈머 전체를 멈춘다 (poison pill)

[← 문서 목록](../README.md) · [README](../../README.md)

| | |
|---|---|
| **발생 단계** | 3단계 — [KS-16](../issues/KS-16-balance-worker.md) |

## 문제

`balance-worker` 를 처음 기동했는데 잔액이 비어 있었다. 토픽에는 이벤트가 60건 넘게 쌓여 있었다.

```json
{ "balances": [], "totalApplied": 0, "totalRejected": 0 }
```

로그를 보니 컨슈머가 **같은 지점에서 무한 재시도**하고 있었다.

```
RecordDeserializationException: Error deserializing key/value
  for partition ledger-events-1 at offset 0.
Caused by: InvalidTypeIdException: Could not resolve subtype of
  [simple type, class ...LedgerEvent]: missing type id property 'eventType'

IllegalStateException: This error handler cannot process 'SerializationException's
  directly; please consider configuring an 'ErrorHandlingDeserializer'
```

## 원인

**[KS-3](../issues/KS-3-kafka-cli-basics.md) CLI 실습 때 넣은 원시 JSON 이 토픽에 그대로 남아 있었다.**

```bash
# KS-3 에서 실행했던 것
printf 'user-1:{"n":1}\n' | kafka-console-producer.sh ...
```

`{"n":1}` 에는 `eventType` 이 없다. `@JsonTypeInfo` 로 타입을 판별하는 컨슈머는
이 메시지를 **어떤 구현체로도 복원할 수 없다.**

그리고 역직렬화는 **리스너에 도달하기 전** 단계에서 일어나므로,
애플리케이션 코드에서 `try-catch` 로 잡을 수 없다.
컨슈머는 그 offset 에서 멈춰 계속 재시도한다.

## 이것이 poison pill 이다

| | |
|---|---|
| 증상 | 메시지 **하나** 때문에 **그 파티션 전체**가 영구히 막힌다 |
| 범위 | 같은 파티션의 뒤쪽 메시지는 전부 처리되지 않는다 |
| 회복 | 스스로 회복하지 않는다. 사람이 개입해야 한다 |

이번에는 파티션 1의 offset 0 이 막혀 **p1 전체가 정지**했다.
(p2 에 있던 정상 이벤트도 결국 못 읽었는데, 컨슈머 스레드가 예외로 계속 죽고 재시작했기 때문이다)

## 해결 (이번에 한 것)

CLI 실습 데이터는 버려도 되는 것이므로 **토픽을 재생성**했다.

```bash
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic ledger-events
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic ledger-events --partitions 3 --replication-factor 1
```

## 제대로 된 해결 (7단계에서)

토픽 재생성은 **학습 환경이라 가능한 선택**이다. 운영에서는 불가능하다.

정석은 `ErrorHandlingDeserializer` 다 — 역직렬화 실패를 예외로 던지는 대신
**실패 정보를 담은 특수 레코드로 바꿔** 리스너까지 흘려보낸다.
그러면 에러 핸들러가 잡아 DLT 로 보낼 수 있다.

```yaml
spring:
  kafka:
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```

**[7단계](../planning/04-roadmap.md)에서 다룬다.** 이 프로젝트는 "문제를 먼저 겪고 해결한다"는 순서를 따르므로,
지금 미리 넣지 않고 이 기록만 남긴다.

## 배운 것

### 역직렬화 실패는 애플리케이션 코드로 잡을 수 없다

`@KafkaListener` 메서드 안에서 `try-catch` 를 아무리 잘 써도 소용없다.
**메시지가 리스너에 도달하기 전에** 실패하기 때문이다.

이것이 `ErrorHandlingDeserializer` 라는 별도 장치가 존재하는 이유다.

### 스키마가 다른 메시지를 같은 토픽에 넣으면 안 된다

CLI 실습과 애플리케이션이 **같은 토픽**을 공유한 것이 화근이었다.
실습용 토픽을 따로 두었어야 했다.

실무에서도 같은 함정이 있다 — 토픽에 스키마가 다른 메시지를 섞으면
컨슈머가 언제든 멈출 수 있다. **Schema Registry 가 존재하는 이유** 중 하나다.

### 7단계의 주제를 3단계에서 미리 만났다

"한 메시지가 파티션 전체를 막는다"는 [실험 F](../planning/01-planning.md)에서 다루려던 현상인데,
의도치 않게 먼저 겪었다.

덕분에 **DLT 가 왜 필요한지**가 이미 몸에 남았다.
7단계에서는 "왜 필요한지"가 아니라 "어떻게 구성하는지"에 집중할 수 있다.
