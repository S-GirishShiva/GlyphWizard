plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.detekt) }
kotlin { jvmToolchain(25) }
dependencies { implementation(libs.coroutines.core) }
detekt {
    buildUponDefaultConfig = true
    ignoreFailures = providers.gradleProperty("canary.ignoreFailures").isPresent
}
