plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "7.2.3.7755"
}

sonar {
    properties {
        property("sonar.projectKey", "Trixi-software_TableSoccerRanker")
        property("sonar.organization", "trixi-software")
        property("sonar.projectName", "TableSoccerRanker")
        // Scan the whole monorepo, not just the gradle project
        property("sonar.projectBaseDir", rootDir.parent!!)
        property(
            "sonar.sources",
            listOf(
                "backend/src/main/java",
                "frontend/src",
                "backend/Dockerfile",
                "frontend/Dockerfile",
                "docker-compose.yml",
            ).joinToString(","),
        )
        property("sonar.tests", "backend/src/test/java")
        property(
            "sonar.exclusions",
            listOf(
                "**/node_modules/**",
                "**/.svelte-kit/**",
                "**/build/**",
                "**/.gradle/**",
                "**/bin/**",
            ).joinToString(","),
        )
    }
}

group = "com.tablesoccer"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

extra["testcontainers.version"] = "1.21.1"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Excel import
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:1.21.1")
    testImplementation("org.testcontainers:postgresql:1.21.1")
    // Force docker-java upgrade for Docker Desktop 4.61+ compatibility
    testImplementation("com.github.docker-java:docker-java-api:3.7.1")
    testImplementation("com.github.docker-java:docker-java-transport-zerodep:3.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Docker Desktop 4.61+ requires minimum API v1.44; docker-java needs this hint
    jvmArgs("-Dapi.version=1.44")
}
