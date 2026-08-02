import java.util.Properties

plugins {
    kotlin("multiplatform") version "2.2.0" apply false
    kotlin("native.cocoapods") version "2.2.0" apply false
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    id("org.jetbrains.compose") version "1.8.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

val versionFile = rootProject.file("version.properties")
require(versionFile.exists()) {
    "Missing $versionFile. Define SDK_VERSION and MEDIASOUP_CLIENT_VERSION before building."
}

val releaseVersions = Properties().apply {
    versionFile.inputStream().use(::load)
}

fun requiredReleaseVersion(name: String): String =
    releaseVersions.getProperty(name)?.trim().orEmpty().also { value ->
        require(value.isNotEmpty()) { "$name must be set in version.properties" }
    }

val sdkVersion = requiredReleaseVersion("SDK_VERSION")
val mediasoupClientVersion = requiredReleaseVersion("MEDIASOUP_CLIENT_VERSION")

extra["sdkVersion"] = sdkVersion
extra["mediasoupClientVersion"] = mediasoupClientVersion

val documentationFiles = listOf(
    rootProject.file("README.md"),
    rootProject.file("README_FULL.md"),
    rootProject.file("EXAMPLES.md")
)
val sdkCoordinatePattern = Regex("""(com\.mediasfu:mediasfu-sdk(?:-android)?):[0-9A-Za-z.-]+""")
val clientCoordinatePattern = Regex("""(com\.mediasfu:mediasoup-client):[0-9A-Za-z.-]+""")

fun synchronizedDocumentation(content: String): String =
    clientCoordinatePattern.replace(
        sdkCoordinatePattern.replace(content) { match ->
            "${match.groupValues[1]}:$sdkVersion"
        }
    ) { match ->
        "${match.groupValues[1]}:$mediasoupClientVersion"
    }

tasks.register("syncDocumentationVersions") {
    group = "documentation"
    description = "Synchronizes SDK and mediasoup-client versions in public documentation."

    inputs.property("sdkVersion", sdkVersion)
    inputs.property("mediasoupClientVersion", mediasoupClientVersion)
    outputs.upToDateWhen { false }

    doLast {
        documentationFiles.forEach { file ->
            file.writeText(synchronizedDocumentation(file.readText()))
        }
        logger.lifecycle(
            "Documentation synchronized to SDK $sdkVersion and mediasoup-client $mediasoupClientVersion"
        )
    }
}

tasks.register("checkDocumentationVersions") {
    group = "verification"
    description = "Verifies public documentation uses the release versions from version.properties."

    inputs.property("sdkVersion", sdkVersion)
    inputs.property("mediasoupClientVersion", mediasoupClientVersion)
    inputs.files(documentationFiles)

    doLast {
        val staleFiles = documentationFiles.filter { file ->
            val content = file.readText()
            synchronizedDocumentation(content) != content
        }

        if (staleFiles.isNotEmpty()) {
            throw GradleException(
                "Documentation versions are stale in ${staleFiles.joinToString { it.name }}. " +
                    "Run ./gradlew syncDocumentationVersions."
            )
        }
    }
}
