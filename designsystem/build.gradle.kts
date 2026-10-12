plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint.gradle)
}

android {
    namespace = "io.github.sgirishshiva.glyphwizard.designsystem"
    compileSdk {
        version = release(37)
    }
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures { compose = true }
    lint { warningsAsErrors = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reflects into jdk.internal.access, which JDK 25 no longer exports (robolectric.org getting started)
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

detekt {
    buildUponDefaultConfig = true
    source.from("src/debug/kotlin", "src/release/kotlin")
}
ktlint { version = libs.versions.ktlint.get() }
kover { reports { variant("debug") { verify { rule { minBound(80) } } } } }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    ktlintRuleset(libs.compose.rules.ktlint)
}
