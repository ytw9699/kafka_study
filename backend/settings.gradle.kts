plugins {
    // 로컬에 JDK 17 이 없으면 toolchain 이 자동으로 내려받게 한다.
    //
    // jvmToolchain(17) 은 "17 을 쓴다"는 선언일 뿐, 없으면 빌드가 실패한다.
    // 처음 클론한 사람이 JDK 17 을 따로 설치하지 않아도 바로 빌드되게 하기 위함이다.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "kafka-study"

include(
    "common",         // 이벤트 DTO — 세 서비스가 공유
    "ledger-api",     // Producer + SSE 게이트웨이 (DB 없음)
    "balance-worker", // 잔액 계산 컨슈머 (여러 인스턴스)
    "fraud-service",  // 나중에 합류하는 컨슈머
)
