# TS-3: 런타임 seek 이 컨슈머를 깨뜨린다 — 콜백 두 종류를 구분해야 한다

[← 문서 목록](../README.md) · [README](../../README.md)

| | |
|---|---|
| **발생 단계** | 5단계 — [KS-22](../issues/KS-22-replay-api.md) |
| **버전** | Spring Kafka 3.2.4 |

## 문제 1 — 콜백이 아예 오지 않았다

`ConsumerSeekAware` 를 **별도 빈**(`ReplayCoordinator`)에 구현했더니 콜백이 한 번도 불리지 않았다.

```
java.lang.IllegalStateException: 아직 파티션을 배정받지 못했습니다
```

### 원인

**`ConsumerSeekAware` 는 `@KafkaListener` 가 붙은 리스너 빈이 구현해야 한다.**
Spring Kafka 는 리스너 빈이 그 인터페이스를 구현했는지 확인해 콜백을 넘긴다.
관계없는 빈에 구현해두면 찾지 못한다.

### 해결

리스너(`LedgerConsumer`)가 구현하고, 받은 콜백을 코디네이터에 위임한다.

---

## 문제 2 — `ConcurrentModificationException`

콜백은 받았는데, REST 요청에서 `seekToBeginning()` 을 부르니 터졌다.

```
java.util.ConcurrentModificationException: KafkaConsumer is not safe for
  multi-threaded access. currentThread(name: http-nio-8082-exec-2, id: 25)
  otherThread(id: 37)

  at KafkaConsumer.seekToBeginning(KafkaConsumer.java:1178)
  at KafkaMessageListenerContainer$ListenerConsumer$InitialOrIdleSeekCallback
       .seekToBeginning(KafkaMessageListenerContainer.java:3741)
```

### 원인 — 콜백이 두 종류이고 동작이 다르다

`ConsumerSeekAware` 는 **두 곳**에서 `ConsumerSeekCallback` 을 준다.
같은 타입이지만 **구현체가 다르다.**

| 출처 | 구현체 | 동작 |
|---|---|---|
| `registerSeekCallback(cb)` | `ListenerConsumer` | **지연 seek** — 큐에 넣고 다음 poll 직전에 적용 |
| `onPartitionsAssigned(assignments, cb)` | `InitialOrIdleSeekCallback` | **즉시 seek** — `consumer.seekToBeginning()` 직접 호출 |

나는 `onPartitionsAssigned` 에서 받은 콜백으로 **덮어썼고**, 그것을 HTTP 스레드에서 불렀다.
즉시 seek 은 컨슈머를 직접 만지므로 다른 스레드에서 부르면 터진다.

### 해결

**`registerSeekCallback` 에서 받은 것만 보관한다.**
`onPartitionsAssigned` 의 콜백은 배정 시점의 초기 위치 지정용이므로 저장하지 않는다.

```kotlin
override fun registerSeekCallback(cb: ConsumerSeekCallback) {
    replay.registerCallback(cb)          // ✅ 지연 seek — 보관한다
}

override fun onPartitionsAssigned(assignments: Map<TopicPartition, Long>, cb: ConsumerSeekCallback) {
    replay.onAssigned(assignments.keys)  // ✅ 파티션만 갱신, cb 는 버린다
}
```

## 배운 것

### 같은 타입이라고 같은 물건이 아니다

둘 다 `ConsumerSeekCallback` 이라 구분 없이 써도 될 것 같았다.
**타입은 같지만 스레드 안전성이 정반대**였다.

인터페이스가 같아도 **구현체의 계약이 다를 수 있다.** 예외 메시지의 클래스명
(`InitialOrIdleSeekCallback`)을 보고서야 알았다.

### 예외 메시지가 정확히 원인을 말해줬다

```
currentThread(name: http-nio-8082-exec-2)  otherThread(id: 37)
```

**HTTP 스레드가 리스너 스레드의 컨슈머를 건드렸다**는 것을 그대로 알려준다.
Kafka 클라이언트가 이 검사를 넣어둔 덕분에 조용한 데이터 손상 대신 명확한 예외가 났다.

### 설계 문서에 적어둔 것이 방향을 잡아줬다

[02. 설계서](../planning/02-architecture.md)에 미리 적어둔 구현 노트가 있었다.

> `ConsumerSeekAware.registerSeekCallback` 에서 받은 콜백을 보관해두고 REST 요청 시 호출한다.
> `KafkaConsumer` 는 thread-safe 하지 않으므로 REST 스레드에서 직접 건드리지 않는다.

**"registerSeekCallback 에서 받은 것"** 이라고 정확히 써 있었는데,
구현하면서 `onPartitionsAssigned` 의 콜백으로 덮어써버렸다.
문서를 다시 읽고 원인을 좁힐 수 있었다.
