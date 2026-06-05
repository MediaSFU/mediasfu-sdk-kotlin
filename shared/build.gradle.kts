import java.io.File

plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.vanniktech.maven.publish")
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    flatDir { dirs("$rootDir/libs") }
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    cocoapods {
        version = project.findProperty("pomVersion")?.toString() ?: "1.0.1"
        summary = "MediaSFU Kotlin Multiplatform SDK"
        homepage = "https://github.com/MediaSFU/mediasfu-sdk-kotlin"
        ios.deploymentTarget = "14.1"

        framework {
            baseName = "MediaSFUSDK"
            isStatic = true
        }

        pod("WebRTC") {
            source = path(project.file("../ios-local-pods/WebRTC"))
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Shared logic dependencies
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("io.ktor:ktor-client-core:2.3.11")
                implementation("io.ktor:ktor-client-websockets:2.3.11")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
                implementation("io.ktor:ktor-client-logging:2.3.11")

                // Socket.IO for real-time communication
                implementation("io.socket:socket.io-client:2.1.0")

                // JSON handling
                implementation("org.json:json:20240303")

                // Compose Multiplatform UI
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)

                // Coil for multiplatform network image loading
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                implementation("io.coil-kt.coil3:coil-network-ktor2:3.0.4")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
        val androidMain by getting {
            dependencies {
                // Mediasoup Android bindings from Maven Central
                compileOnly("com.mediasfu:mediasoup-client:1.0.1")
                implementation("io.ktor:ktor-client-okhttp:2.3.11")

                // ML Kit Selfie Segmentation for virtual backgrounds
                implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

                // Coroutines extension for ML Kit Tasks API
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

                // Activity Compose for image picker and result launchers
                implementation("androidx.activity:activity-compose:1.9.3")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation("io.mockk:mockk:1.13.12")
            }
        }
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.11")
            }
        }
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }

    jvmToolchain(17)
}

android {
    namespace = "com.mediasfu.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Maven Central Publishing with Vanniktech plugin
mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.mediasfu", "mediasfu-sdk", "1.0.1")

    pom {
        name.set("MediaSFU Kotlin Multiplatform SDK")
        description.set("Production-ready Kotlin Multiplatform SDK for real-time communication with MediaSFU. Supports video conferencing, webinars, broadcasts, and audio-only spaces.")
        inceptionYear.set("2025")
        url.set("https://github.com/MediaSFU/mediasfu-sdk-kotlin")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("mediasfu")
                name.set("MediaSFU Team")
                email.set("info@mediasfu.com")
                url.set("https://mediasfu.com")
            }
        }

        scm {
            url.set("https://github.com/MediaSFU/mediasfu-sdk-kotlin")
            connection.set("scm:git:git://github.com/MediaSFU/mediasfu-sdk-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/MediaSFU/mediasfu-sdk-kotlin.git")
        }
    }
}

fun resolveActiveDeveloperDir(): File {
    val envDeveloperDir = System.getenv("DEVELOPER_DIR")?.trim().orEmpty()
    if (envDeveloperDir.isNotEmpty()) {
        return File(envDeveloperDir)
    }

    val selectedDeveloperDir = runCatching {
        val process = ProcessBuilder("xcode-select", "-p")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (process.waitFor() == 0 && output.isNotEmpty()) output else null
    }.getOrNull()

    return File(selectedDeveloperDir ?: "/Applications/Xcode.app/Contents/Developer")
}

fun generateDarwinFoundationOverlay(): File? {
    val developerDir = resolveActiveDeveloperDir()
    val overlayDir = layout.buildDirectory.dir("cinterop/vfs").get().asFile.apply { mkdirs() }
    val sdkRoots = listOf("iPhoneOS", "iPhoneSimulator").mapNotNull { sdkPlatform ->
        val sdkDir = developerDir
            .resolve("Platforms/$sdkPlatform.platform/Developer/SDKs")
            .listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(sdkPlatform) && it.name.endsWith(".sdk") }
            ?.sortedByDescending { it.name }
            ?.firstOrNull()
            ?: return@mapNotNull null

        val includeDir = sdkDir.resolve("usr/include")
        val moduleMap = includeDir.resolve("DarwinFoundation1.modulemap")
        if (!moduleMap.exists()) {
            return@mapNotNull null
        }

        val originalText = moduleMap.readText()
        if (!originalText.contains("found_incompatible_headers__check_search_paths")) {
            return@mapNotNull null
        }

        val fixedModuleMap = overlayDir.resolve("${sdkPlatform}-DarwinFoundation1-fixed.modulemap")
        fixedModuleMap.writeText(
            originalText
                .lineSequence()
                .filterNot { it.contains("found_incompatible_headers__check_search_paths") }
                .joinToString(separator = "\n", postfix = "\n")
        )

        includeDir to fixedModuleMap
    }

    if (sdkRoots.isEmpty()) {
        return null
    }

    val overlayJson = buildString {
        appendLine("{")
        appendLine("  \"version\": 0,")
        appendLine("  \"case-sensitive\": \"false\",")
        appendLine("  \"roots\": [")
        sdkRoots.forEachIndexed { index, (includeDir, fixedModuleMap) ->
            appendLine("    {")
            appendLine("      \"type\": \"directory\",")
            appendLine("      \"name\": \"${includeDir.absolutePath}\",")
            appendLine("      \"contents\": [")
            appendLine("        {")
            appendLine("          \"type\": \"file\",")
            appendLine("          \"name\": \"DarwinFoundation1.modulemap\",")
            appendLine("          \"external-contents\": \"${fixedModuleMap.absolutePath}\"")
            appendLine("        }")
            append("      ]\n    }")
            if (index != sdkRoots.lastIndex) {
                append(',')
            }
            appendLine()
        }
        appendLine("  ]")
        appendLine("}")
    }

    return overlayDir.resolve("cinterop-vfs-overlay.yaml").apply {
        writeText(overlayJson)
    }
}

// Workaround: Xcode 26.2 ships DarwinFoundation1.modulemap with a guarded 'requires'
// clause that Kotlin/Native's bundled clang doesn't understand. Generate a local VFS
// overlay for both device and simulator SDKs so cinterop can reuse fixed copies.
val darwinFoundationOverlay = generateDarwinFoundationOverlay()

tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
    darwinFoundationOverlay?.let { overlayFile ->
        settings.compilerOpts("-ivfsoverlay", overlayFile.absolutePath)
    }
}
