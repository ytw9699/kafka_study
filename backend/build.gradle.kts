import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.25" apply false
    // kotlin-spring = allopen 프리셋.
    // Kotlin 클래스는 기본이 final 인데 Spring 의 CGLIB 프록시(@Configuration, @Transactional 등)는
    // 상속이 가능해야 하므로, 해당 애노테이션이 붙은 클래스를 자동으로 open 으로 만들어준다.
    kotlin("plugin.spring") version "1.9.25" apply false
    // kotlin-jpa = noarg 프리셋.
    // JPA 는 리플렉션으로 엔티티를 만들 때 기본 생성자를 요구하는데 Kotlin 에는 없다.
    // @Entity 등이 붙은 클래스에 기본 생성자를 자동 생성해준다. (KS-5 이후 엔티티에서 사용)
    kotlin("plugin.jpa") version "1.9.25" apply false
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.archseoul.kafkastudy"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")

    // JDK 17 고정. 로컬에 어떤 JDK 가 깔려 있든 toolchain 이 17 을 찾아 쓴다.
    //
    // subprojects {} 블록에서는 `kotlin { }` 같은 타입 안전 접근자를 쓸 수 없다.
    // 플러그인이 런타임에 apply 되므로 스크립트 컴파일 시점에는 확장이 존재하지 않기 때문이다.
    // 그래서 extensions.configure 로 명시한다.
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(17)
    }

    // Spring Boot BOM 을 가져와 버전을 통일한다.
    // 이렇게 해두면 각 모듈에서 의존성 버전을 적지 않아도 된다.
    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
        }
    }

    dependencies {
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        // Kotlin 의 null 안전성 · data class 를 Jackson 이 제대로 다루게 해준다.
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
        // Instant 등 java.time 타입 지원. 없으면 직렬화 자체가 실패한다.
        "implementation"("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

        "testImplementation"(kotlin("test"))
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            // JSR-305 애노테이션(@Nullable 등)을 엄격하게 해석해
            // 자바 라이브러리와의 경계에서도 null 안전성을 유지한다.
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
