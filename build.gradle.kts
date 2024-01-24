plugins {
    kotlin("jvm") version "1.9.21"
    application
}

group = "io.ktor-test"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

application {
    mainClass = "io.ktor.perf.MainKt"
}

val ktor_version = "3.0.0-SNAPSHOT" // "2.3.7"

dependencies {

    implementation("io.ktor:ktor-network-tls-certificates:$ktor_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    // implementation("io.ktor:ktor-server-cio-jvm")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("ch.qos.logback:logback-classic:1.4.12")

    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-apache5:$ktor_version")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}