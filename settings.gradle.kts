pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "mediasfu-sdk-kotlin"
include(":shared")

// Example apps demonstrating different usage patterns
include(":sample-app")     // Basic full-featured sample
include(":demo-app")       // Minimal integration demo
include(":spacestek-app")  // Twitter Spaces-like audio rooms
