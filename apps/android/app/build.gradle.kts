import com.android.build.api.artifact.SingleArtifact
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

fun releaseProp(name: String): String? =
    (providers.environmentVariable(name).orNull ?: providers.gradleProperty(name).orNull)?.takeIf { it.isNotBlank() }

// Short commit for About; "+dirty" when tracked files differ from HEAD (diff --quiet: only the exit code is a config input,
// so edits don't invalidate the configuration cache). "unknown" without git or outside a checkout.
val gitHash: String =
    runCatching {
        providers
            .exec { commandLine("git", "rev-parse", "--short", "HEAD") }
            .standardOutput.asText
            .get()
            .trim()
    }.getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?.let { sha ->
            val dirty =
                runCatching {
                    providers
                        .exec {
                            commandLine("git", "diff", "--quiet", "HEAD")
                            isIgnoreExitValue = true
                        }.result
                        .get()
                        .exitValue != 0
                }.getOrDefault(false)
            if (dirty) "$sha+dirty" else sha
        } ?: "unknown"

/**
 * assets/contributors.json for About. Release: GitHub's contributors API (GITHUB_TOKEN if set, 5 s timeouts), reduced to
 * login/html_url/avatar_url/contributions of users with GitHub https links; any failure keeps [fallback]. Other builds: [fallback].
 */
abstract class ContributorsTask : DefaultTask() {
    @get:Input abstract val fetch: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fallback: RegularFileProperty

    // Internal: a credential, not a build input.
    @get:Internal abstract val token: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    init {
        // The fetched list changes without any input changing.
        outputs.upToDateWhen { !fetch.get() }
    }

    @TaskAction
    fun run() {
        val out = outputDir.get().file("contributors.json").asFile
        out.parentFile.mkdirs()
        val fallbackText = fallback.get().asFile.readText()
        if (!fetch.get()) return out.writeText(fallbackText)
        val text =
            runCatching {
                val c = URI("https://api.github.com/repos/Venipa/stashling/contributors").toURL().openConnection() as HttpURLConnection
                c.connectTimeout = 5_000
                c.readTimeout = 5_000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "stashling-build")
                token.orNull?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
                val list = JsonSlurper().parseText(c.inputStream.bufferedReader().use { it.readText() }) as List<*>
                val clean =
                    list.filterIsInstance<Map<*, *>>().filter { it["type"] == "User" }.mapNotNull { m ->
                        val login = m["login"] as? String ?: return@mapNotNull null
                        val html = (m["html_url"] as? String)?.takeIf { it.startsWith("https://github.com/") } ?: return@mapNotNull null
                        val avatar =
                            (m["avatar_url"] as? String)?.takeIf { it.startsWith("https://avatars.githubusercontent.com/") }
                                ?: return@mapNotNull null
                        mapOf(
                            "login" to login,
                            "html_url" to html,
                            "avatar_url" to avatar,
                            "contributions" to ((m["contributions"] as? Number)?.toInt() ?: 0),
                        )
                    }
                check(clean.isNotEmpty()) { "empty list" }
                JsonOutput.prettyPrint(JsonOutput.toJson(clean))
            }.getOrElse {
                logger.warn("Contributors: GitHub fetch failed (${it.message}); using ${fallback.get().asFile.name}.")
                fallbackText
            }
        out.writeText(text)
    }
}

android {
    namespace = "net.venipa.stashling"
    // Compose 1.13 (BOM 2026.11.01) needs compileSdk 37.1+.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "net.venipa.stashling"
        minSdk = 31
        targetSdk = 37
        // Release builds: release.ps1/release.sh pass the version; local builds keep the defaults.
        versionCode =
            releaseProp("STASHLING_VERSION_CODE")?.let { raw ->
                val code = raw.toIntOrNull()
                require(code != null && code >= 1) { "STASHLING_VERSION_CODE must be a whole number of at least 1, got '$raw'" }
                code
            } ?: 1
        versionName = releaseProp("STASHLING_VERSION_NAME") ?: "0.1.0"
        manifestPlaceholders["appLabel"] = "@string/app_name"
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
    }

    // The app is English only: drop the translations AndroidX and Material bundle. Add locales with translations.
    androidResources { localeFilters += "en" }

    // The signing key, from env or Gradle properties; without all four the release build stays unsigned.
    val keystore = releaseProp("STASHLING_KEYSTORE_FILE")
    val storePass = releaseProp("STASHLING_KEYSTORE_PASSWORD")
    val alias = releaseProp("STASHLING_KEY_ALIAS")
    val keyPass = releaseProp("STASHLING_KEY_PASSWORD")
    val signingSet = listOf(keystore, storePass, alias, keyPass).count { it != null }
    if (signingSet in 1..3) {
        logger.warn("Release signing: $signingSet of 4 STASHLING_KEYSTORE_*/STASHLING_KEY_* set; release stays unsigned.")
    }
    val releaseKey =
        if (signingSet == 4) {
            signingConfigs.create("release") {
                storeFile = file(keystore!!)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        } else {
            null
        }

    buildTypes {
        // The dev build installs beside the release one (own data and accounts); its label says which is which.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "Stashling Dev"
        }
        release {
            signingConfig = releaseKey
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// The merged manifest of every variant asks for exactly these permissions. A new permission fails the build until it
// is added here on purpose. Planned: POST_NOTIFICATIONS with the download watch.
val expectedPermissions =
    sortedSetOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        // Download notifications (Whisparr watch).
        "android.permission.POST_NOTIFICATIONS",
        // Android 17 local network protection: LAN Whisparr.
        "android.permission.ACCESS_LOCAL_NETWORK",
        // In-app updater (GitHub releases).
        "android.permission.REQUEST_INSTALL_PACKAGES",
        // androidx.biometric (app lock).
        "android.permission.USE_BIOMETRIC",
        "android.permission.USE_FINGERPRINT",
        // WorkManager merges these in: wake lock for a running job, boot receiver to re-arm periodic work, foreground service for expedited jobs.
        "android.permission.WAKE_LOCK",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.FOREGROUND_SERVICE",
        // androidx.core's guard for receivers registered at run time; not a user permission.
        "{applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    )
val checkPermissionSet = tasks.register("checkPermissionSet")
androidComponents {
    onVariants { variant ->
        val contributors =
            tasks.register<ContributorsTask>("generate${variant.name.replaceFirstChar(Char::uppercase)}Contributors") {
                fetch.set(variant.buildType == "release")
                fallback.set(layout.projectDirectory.file("contributors.json"))
                token.set(providers.environmentVariable("GITHUB_TOKEN"))
                outputDir.set(layout.buildDirectory.dir("generated/contributors/${variant.name}"))
            }
        variant.sources.assets?.addGeneratedSourceDirectory(contributors, ContributorsTask::outputDir)
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val perVariant =
            tasks.register("check${variant.name.replaceFirstChar(Char::uppercase)}PermissionSet") {
                inputs.file(manifest)
                // A local copy: the configuration cache cannot keep a reference to the script.
                val expected = expectedPermissions.map { it.replace("{applicationId}", variant.applicationId.get()) }.toSortedSet()
                doLast {
                    val f = manifest.get().asFile
                    val doc =
                        DocumentBuilderFactory
                            .newInstance()
                            .apply { isNamespaceAware = true }
                            .newDocumentBuilder()
                            .parse(f)
                    val found =
                        listOf("uses-permission", "uses-permission-sdk-23")
                            .flatMap { tag -> doc.getElementsByTagName(tag).let { n -> (0 until n.length).map { n.item(it) as Element } } }
                            .map { it.getAttributeNS("http://schemas.android.com/apk/res/android", "name") }
                            .toSortedSet()
                    check(found == expected) {
                        "$f asks for $found, expected exactly $expected: extra ${found - expected}, missing ${expected - found}"
                    }
                }
            }
        checkPermissionSet.configure { dependsOn(perVariant) }
    }
}
tasks.named("check") { dependsOn(checkPermissionSet) }
tasks
    .matching {
        it.name in setOf("assembleRelease", "assembleDebug", "bundleRelease", "bundleDebug")
    }.configureEach { dependsOn(checkPermissionSet) }

dependencies {
    implementation(project(":core"))
    implementation(project(":design-system"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testRuntimeOnly(libs.junit.platform.launcher)
}
