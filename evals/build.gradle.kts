plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint.gradle)
}

kotlin { jvmToolchain(25) }
detekt { buildUponDefaultConfig = true }
ktlint { version = libs.versions.ktlint.get() }
application { mainClass = "io.github.sgirishshiva.glyphwizard.evals.MainKt" }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }
