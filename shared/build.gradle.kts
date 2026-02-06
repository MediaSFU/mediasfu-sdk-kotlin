plugins {
    kotlin("multiplatform")
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

                // Coil for network image loading
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
                implementation("io.mockk:mockk:1.13.12")
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
        val androidUnitTest by getting
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
