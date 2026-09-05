rootProject.name = "kafka-study"

include(
    "common",         // 이벤트 DTO — 세 서비스가 공유
    "ledger-api",     // Producer + SSE 게이트웨이 (DB 없음)
    "balance-worker", // 잔액 계산 컨슈머 (여러 인스턴스)
    "fraud-service",  // 나중에 합류하는 컨슈머
)
