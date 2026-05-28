package com.mediasfu.sdk.unity

import android.app.Application
import android.content.Context

internal object AndroidUnityWebRtcJniBridge {
    private val stateLock = Any()

    @Volatile
    private var registryFactory: (() -> AndroidUnityWebRtcEngineRegistry)? = null

    @Volatile
    private var registry: AndroidUnityWebRtcEngineRegistry? = null

    @Volatile
    private var lastCreateEngineError: String = ""

    @JvmStatic
    fun createEngine(payloadJson: String?): Long {
        return runCatching {
            registry().createEngine(payloadJson.orEmpty())
        }.onFailure { error ->
            synchronized(stateLock) {
                lastCreateEngineError = error.message
                    ?: "MediaSFU Android JNI bridge failed to create a Unity WebRTC engine."
            }
        }.getOrDefault(0L)
    }

    @JvmStatic
    fun destroyEngine(handle: Long) {
        registryOrNull()?.destroyEngine(handle)
    }

    @JvmStatic
    fun invoke(handle: Long, operationName: String?, payloadJson: String?): String {
        return registry().invoke(handle, operationName.orEmpty(), payloadJson.orEmpty())
    }

    @JvmStatic
    fun consumeLastCreateEngineError(): String {
        return synchronized(stateLock) {
            val detail = lastCreateEngineError
            lastCreateEngineError = ""
            detail
        }
    }

    @JvmStatic
    fun describe(): String {
        return "MediaSFU Android JNI Unity WebRTC bridge"
    }

    internal fun installRegistryFactoryForTests(factory: () -> AndroidUnityWebRtcEngineRegistry) {
        synchronized(stateLock) {
            registryFactory = factory
            registry = null
            lastCreateEngineError = ""
        }
    }

    internal fun resetForTests() {
        synchronized(stateLock) {
            registryFactory = null
            registry = null
            lastCreateEngineError = ""
        }
    }

    private fun registry(): AndroidUnityWebRtcEngineRegistry {
        registryOrNull()?.let { return it }

        return synchronized(stateLock) {
            registry ?: buildDefaultRegistry().also { registry = it }
        }
    }

    private fun registryOrNull(): AndroidUnityWebRtcEngineRegistry? {
        return synchronized(stateLock) { registry }
    }

    private fun buildDefaultRegistry(): AndroidUnityWebRtcEngineRegistry {
        val factory = registryFactory
        if (factory != null) {
            return factory()
        }

        return AndroidUnityWebRtcEngineRegistry(resolveApplicationContext())
    }

    private fun resolveApplicationContext(): Context {
        val currentApplication = runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
            currentApplicationMethod.invoke(null) as? Application
        }.getOrNull()

        if (currentApplication != null) {
            return currentApplication.applicationContext
        }

        val fallbackApplication = runCatching {
            val appGlobalsClass = Class.forName("android.app.AppGlobals")
            val initialApplicationMethod = appGlobalsClass.getMethod("getInitialApplication")
            initialApplicationMethod.invoke(null) as? Application
        }.getOrNull()

        return fallbackApplication?.applicationContext
            ?: throw IllegalStateException(
                "MediaSFU Android JNI bridge could not resolve an application context. Load the bridge after the Android application has started, or install a test registry factory before invoking it."
            )
    }
}