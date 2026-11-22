plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9 has built-in Kotlin; declaring the Kotlin plugins here only pins the Kotlin Gradle plugin version.
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // LF everywhere so Windows and Linux CI agree.
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        // Composables are PascalCase by convention.
        ktlint(ktlintVersion).editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}
