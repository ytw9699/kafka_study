# TS-1: KRaft 브로커가 `advertised.listeners ... 0.0.0.0` 로 기동 실패

| | |
|---|---|
| **발생 단계** | 0단계 — [KS-2](../issues/KS-2-infra-docker-compose.md) |
| **이미지** | `apache/kafka:3.9.0` |

## 문제

`docker compose up -d` 시 kafka 컨테이너가 즉시 종료(exit 1).

```
Exception in thread "main" java.lang.IllegalArgumentException: requirement failed:
advertised.listeners cannot use the nonroutable meta-address 0.0.0.0.
Use a routable IP address.
  at kafka.server.KafkaConfig.validateValues(KafkaConfig.scala:1022)
  at kafka.tools.StorageTool$.execute(StorageTool.scala:79)
```

설정은 이랬다.

```yaml
KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092
```

`advertised.listeners` 어디에도 `0.0.0.0`이 없는데 에러가 났다.

## 원인

**`CONTROLLER`가 `advertised.listeners`에 빠져 있었다.**

Kafka는 `listeners`에는 있는데 `advertised.listeners`에 없는 리스너에 대해
**`listeners` 값을 그대로 가져다 쓴다.** 그래서 `CONTROLLER://0.0.0.0:9093`이 되고,
`0.0.0.0`은 광고 주소로 쓸 수 없으므로 검증에서 걸린다.

이미지의 기본 설정(`/etc/kafka/docker/server.properties`)을 보면 실제로 CONTROLLER가 포함되어 있다.

```properties
advertised.listeners=PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093
```

즉 **커스텀 리스너를 쓸 때 CONTROLLER를 빠뜨리는 것이 함정**이다.
`KAFKA_LISTENERS`만 재정의하고 `KAFKA_ADVERTISED_LISTENERS`를 브로커용만 적으면 이 증상이 난다.

## 해결

`advertised.listeners`에도 CONTROLLER를 명시한다.

```yaml
KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092,CONTROLLER://kafka:9093
```

## 진단 방법

에러 메시지만으로는 어느 리스너가 문제인지 알 수 없다. 이미지가 생성하는 최종 설정을 직접 확인하는 것이 빠르다.

```bash
# 이미지의 기본 설정 (여기에 CONTROLLER 가 포함된 것을 보고 원인을 알았다)
docker run --rm --entrypoint bash apache/kafka:3.9.0 \
  -c "grep -vE '^#|^$' /etc/kafka/docker/server.properties"
```

## 배운 것

### `advertised.listeners`는 `listeners`의 부분집합이 아니다 — 각각 독립적으로 완전해야 한다

`listeners`는 **"어디에 바인딩할 것인가"**, `advertised.listeners`는 **"클라이언트에게 뭐라고 알려줄 것인가"** 다.
둘은 목적이 다르므로 각각 모든 리스너를 커버해야 하고, 한쪽에만 있으면 나머지 한쪽 값을 빌려 쓴다.

### 이 에러는 "연결이 안 되는" 부류가 아니라 "아예 안 뜨는" 부류다 — 그나마 다행이다

`advertised.listeners`가 **문법적으로는 유효하지만 도달 불가능한 주소**일 때가 진짜 골치 아프다.
그때는 브로커가 정상 기동하고 연결도 되는 것처럼 보이다가 **produce/consume 단계에서 타임아웃**이 난다.
클라이언트가 부트스트랩 후 받은 광고 주소로 다시 접속하기 때문이다.

이번 건은 기동 자체가 막혀서 원인을 빨리 찾을 수 있었다.
