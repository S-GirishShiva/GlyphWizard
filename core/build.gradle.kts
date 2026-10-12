import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint.gradle)
}

java { targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<JavaCompile>().configureEach { options.release = 17 }
kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // ships inside the APK and runs on ART, which only has Java 17-level JDK APIs (D54)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}
detekt { buildUponDefaultConfig = true }
ktlint { version = libs.versions.ktlint.get() }
kover { reports { verify { rule { minBound(100) } } } }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }
