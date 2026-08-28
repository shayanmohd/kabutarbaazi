// :domain — pure Kotlin/JVM. No Android or framework types, so username rules, phone
// validation, price formatting, media caps and moderation rules stay fully unit-testable
// without an emulator. This module is the fast correctness gate: ./gradlew :domain:test
//
// The models here carry @Serializable and the snake_case names PostgREST returns, and the app
// uses them directly. A parallel set of DTOs that mapped one-to-one onto these would be exactly
// the single-use abstraction the project guidelines forbid.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
