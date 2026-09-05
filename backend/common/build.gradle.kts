// 이벤트 DTO 만 담는 순수 라이브러리 모듈.
// Spring Boot 실행 가능 jar 가 아니므로 bootJar 를 만들지 않는다.
plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
