import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.diffplug.spotless") version "6.22.0"
    id("org.flywaydb.flyway") version "9.22.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.maple"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.kafka:spring-kafka")
    
    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    
    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // OpenAPI documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
    
    // SFTP
    implementation("com.jcraft:jsch:0.1.55")
    implementation("org.apache.commons:commons-vfs2:2.9.0")
    
    // XML processing for ISO20022
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:3.0.2")
    
    // Observability
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry:opentelemetry-api:1.32.0")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:1.32.0-alpha")
    
    // Utilities
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io:2.11.0")
    
    // Optional: Lombok for DTOs
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.awaitility:awaitility:4.2.0")
    
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.19.3")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
}

tasks.withType<BootJar> {
    archiveFileName.set("maple-payments-hub.jar")
}

spotless {
    java {
        googleJavaFormat("1.17.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

flyway {
    url = "jdbc:postgresql://localhost:5432/maple_payments"
    user = "maple_user"
    password = "maple_password"
    locations = arrayOf("classpath:db/migration")
}

// Custom task for running seed data
tasks.register<JavaExec>("runSeed") {
    group = "application"
    description = "Run seed data generation"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.maple.util.SeedDataApplication")
    systemProperty("spring.profiles.active", "dev")
}
