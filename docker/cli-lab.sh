#!/usr/bin/env bash
# KS-3 — Kafka CLI 실습
#
# Spring 이 감춰주기 전에 Kafka 가 큐와 다르다는 것을 CLI 수준에서 확인한다.
# 사용법:  ./docker/cli-lab.sh [실험번호]     (번호 생략 시 전체 실행)
#
# 전제:  docker compose -f docker/docker-compose.yml up -d

set -uo pipefail

K="docker exec kafka /opt/kafka/bin"
KI="docker exec -i kafka /opt/kafka/bin"
BS="localhost:9092"
TOPIC="ledger-events"

produce() { # produce <"key:value" 줄들>
  $KI/kafka-console-producer.sh --bootstrap-server $BS --topic $TOPIC \
    --property parse.key=true --property key.separator=: 2>&1 | grep -v WARN
}

hr() { echo; echo "════════ $* ════════"; }

# ─────────────────────────────────────────────────────────────────────────────
exp0_setup() {
  hr "0. 토픽 생성 (파티션 3)"
  $K/kafka-topics.sh --bootstrap-server $BS --create --topic $TOPIC \
    --partitions 3 --replication-factor 1 2>&1 | grep -v WARN || echo "이미 존재"
  $K/kafka-topics.sh --bootstrap-server $BS --describe --topic $TOPIC
}

# ─────────────────────────────────────────────────────────────────────────────
exp1_key_partition() {
  hr "1. 같은 키 → 항상 같은 파티션"
  for i in $(seq 1 12); do printf "user-$i:{\"n\":1}\n"; done | produce

  echo "--- 키별 파티션 배치 ---"
  $K/kafka-console-consumer.sh --bootstrap-server $BS --topic $TOPIC \
    --from-beginning --timeout-ms 8000 \
    --property print.key=true --property print.partition=true 2>/dev/null \
    | awk '{print $2, $1}' | sort -u -k1,1V

  echo
  echo "→ 같은 키는 항상 같은 파티션. 이것이 순서 보장의 기반이다."
  echo "→ 단, 해시 분포는 균등하지 않다. 특정 파티션에 몰릴 수 있다 (hot partition)."
}

# ─────────────────────────────────────────────────────────────────────────────
exp2_broadcast() {
  hr "2. 서로 다른 컨슈머 그룹 → 둘 다 전부 받는다 (브로드캐스트)"
  for G in group-A group-B; do
    N=$($K/kafka-console-consumer.sh --bootstrap-server $BS --topic $TOPIC \
        --group $G --from-beginning --timeout-ms 6000 2>/dev/null | wc -l | tr -d ' ')
    echo "  $G 수신: $N 건"
  done
  echo
  echo "→ 이 프로젝트의 gateway-group(SSE 중계)이 쓰는 방식."
}

# ─────────────────────────────────────────────────────────────────────────────
exp3_split() {
  hr "3. 같은 컨슈머 그룹 인스턴스 3개 → 파티션을 나눠 갖는다 (분배)"
  local G="group-split-$(date +%s)"

  # ★ 컨슈머를 먼저 붙이고 그 다음 발행해야 깔끔하게 관찰된다.
  #   먼저 발행하면 앞서 붙은 컨슈머가 다 소비해버려 뒤에 합류한 인스턴스가 0건이 된다.
  for i in 1 2 3; do
    ( $K/kafka-console-consumer.sh --bootstrap-server $BS --topic $TOPIC \
        --group $G --timeout-ms 20000 --property print.partition=true \
        2>/dev/null > /tmp/kslab-s$i.txt ) &
  done
  sleep 8
  echo "  (컨슈머 3개 합류 완료, 발행 시작)"
  for i in $(seq 1 12); do printf "user-$i:{\"seq\":$i}\n"; done | produce
  wait

  local total=0
  for i in 1 2 3; do
    local n; n=$(wc -l < /tmp/kslab-s$i.txt | tr -d ' ')
    total=$((total + n))
    echo "  인스턴스 $i: ${n}건  담당파티션=$(grep -o 'Partition:[0-9]' /tmp/kslab-s$i.txt | sort -u | tr '\n' ' ')"
  done
  echo "  합계: ${total}건 (발행 12건)"
  echo
  echo "→ 각자 다른 파티션. 합쳐야 전체가 된다."
  echo "→ 이 프로젝트의 balance-group(잔액 워커)이 쓰는 방식."
}

# ─────────────────────────────────────────────────────────────────────────────
exp4_offsets() {
  hr "4. 그룹별 독립 offset"
  for G in group-A group-B; do
    echo "--- $G ---"
    $K/kafka-consumer-groups.sh --bootstrap-server $BS --describe --group $G 2>/dev/null \
      | grep -v '^$'
  done
  echo
  echo "→ 같은 토픽인데 그룹마다 CURRENT-OFFSET 과 LAG 를 따로 갖는다."
}

# ─────────────────────────────────────────────────────────────────────────────
exp5_replay() {
  hr "5. --from-beginning — 이미 소비된 메시지를 다시 읽는다"
  N=$($K/kafka-console-consumer.sh --bootstrap-server $BS --topic $TOPIC \
      --from-beginning --timeout-ms 8000 2>/dev/null | wc -l | tr -d ' ')
  echo "  수신: $N 건"
  echo
  echo "→ 소비해도 로그가 남기 때문에 가능하다. 큐라면 불가능하다."
}

# ─────────────────────────────────────────────────────────────────────────────
exp6_cap() {
  hr "6. 컨슈머 4개 > 파티션 3개 → 하나는 논다"
  local G="group-cap-$(date +%s)"
  for i in 1 2 3 4; do
    ( $K/kafka-console-consumer.sh --bootstrap-server $BS --topic $TOPIC \
        --group $G --timeout-ms 14000 --property print.partition=true \
        2>/dev/null > /tmp/kslab-cap$i.txt ) &
  done
  sleep 7
  for i in $(seq 1 9); do printf "u$i:{\"x\":$i}\n"; done | produce
  wait
  for i in 1 2 3 4; do
    echo "  인스턴스 $i: $(wc -l < /tmp/kslab-cap$i.txt | tr -d ' ')건  파티션=$(grep -o 'Partition:[0-9]' /tmp/kslab-cap$i.txt | sort -u | tr '\n' ' ')"
  done
  echo
  echo "→ 파티션 수가 병렬 처리의 상한이다. 컨슈머만 늘려도 소용없다."
  echo "→ 4단계에서 balance-worker 를 늘릴 때 그대로 적용되는 제약."
}

# ─────────────────────────────────────────────────────────────────────────────
case "${1:-all}" in
  0) exp0_setup ;;
  1) exp1_key_partition ;;
  2) exp2_broadcast ;;
  3) exp3_split ;;
  4) exp4_offsets ;;
  5) exp5_replay ;;
  6) exp6_cap ;;
  all)
    exp0_setup; exp1_key_partition; exp2_broadcast; exp3_split
    exp4_offsets; exp5_replay; exp6_cap
    hr "완료"
    ;;
  *) echo "사용법: $0 [0|1|2|3|4|5|6|all]"; exit 1 ;;
esac
