plugins {
    kotlin("jvm") version "1.9.21"
    id("io.ktor.plugin") version "2.3.7"
    application
}

group = "io.ktor-test"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass = "io.ktor.perf.MainKt"
}

dependencies {

    implementation("io.ktor:ktor-network-tls-certificates")
    implementation("io.ktor:ktor-server-core-jvm")
    // implementation("io.ktor:ktor-server-cio-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("ch.qos.logback:logback-classic:1.4.12")

    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-apache5")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}