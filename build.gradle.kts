import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// All modules emit JVM 17 bytecode; detekt's embedded compiler must match
// (it cannot follow a newer build JDK).
private val detektJvmTarget = "17"

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach { jvmTarget = detektJvmTarget }
tasks.withType<DetektCreateBaselineTask>().configureEach { jvmTarget = detektJvmTarget }

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    tasks.withType<Detekt>().configureEach { jvmTarget = detektJvmTarget }
    tasks.withType<DetektCreateBaselineTask>().configureEach { jvmTarget = detektJvmTarget }
}
