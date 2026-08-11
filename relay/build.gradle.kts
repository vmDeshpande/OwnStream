plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":protocol"))
    
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.websockets)
    
    implementation(libs.logback.classic)
    
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.h2)
    implementation(libs.hikaricp)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktorClientCore)
    testImplementation(libs.ktorClientContentNegotiation)
    testImplementation(libs.ktorClientWebsockets)
    testImplementation(libs.ktor.serialization.json)
    testImplementation(libs.junit)
}
