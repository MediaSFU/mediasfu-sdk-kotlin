#if defined(__ANDROID__)

#include "MediaSfuUnityBridgeBackend.h"

#include <jni.h>
#include <stdint.h>

static JavaVM* g_media_sfu_java_vm = NULL;
static jclass g_bridge_class = NULL;
static jmethodID g_create_engine_method = NULL;
static jmethodID g_destroy_engine_method = NULL;
static jmethodID g_invoke_method = NULL;
static jmethodID g_consume_create_engine_error_method = NULL;

static void* media_sfu_android_backend_create_engine(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut);

static void media_sfu_android_backend_destroy_engine(
    void* installContext,
    void* backendEngine);

static char* media_sfu_android_backend_invoke(
    void* installContext,
    void* backendEngine,
    const char* operationName,
    const char* payloadJson);

static const char* media_sfu_android_backend_describe(
    void* installContext);

static const MediaSfuUnityWebRtcBackend MEDIA_SFU_ANDROID_JNI_BACKEND =
{
    media_sfu_android_backend_create_engine,
    media_sfu_android_backend_destroy_engine,
    media_sfu_android_backend_invoke,
    media_sfu_android_backend_describe
};

static int media_sfu_android_get_env(JNIEnv** envOut, int* didAttachOut)
{
    JNIEnv* env = NULL;
    jint get_env_result;

    if (envOut == NULL || didAttachOut == NULL || g_media_sfu_java_vm == NULL)
    {
        return 0;
    }

    *didAttachOut = 0;
    get_env_result = (*g_media_sfu_java_vm)->GetEnv(g_media_sfu_java_vm, (void**)&env, JNI_VERSION_1_6);
    if (get_env_result == JNI_OK)
    {
        *envOut = env;
        return 1;
    }

    if (get_env_result != JNI_EDETACHED)
    {
        return 0;
    }

    if ((*g_media_sfu_java_vm)->AttachCurrentThread(g_media_sfu_java_vm, &env, NULL) != JNI_OK)
    {
        return 0;
    }

    *didAttachOut = 1;
    *envOut = env;
    return 1;
}

static void media_sfu_android_release_env(int didAttach)
{
    if (didAttach && g_media_sfu_java_vm != NULL)
    {
        (*g_media_sfu_java_vm)->DetachCurrentThread(g_media_sfu_java_vm);
    }
}

static void media_sfu_android_clear_exception(JNIEnv* env)
{
    if (env != NULL && (*env)->ExceptionCheck(env))
    {
        (*env)->ExceptionClear(env);
    }
}

static char* media_sfu_android_duplicate_jstring(JNIEnv* env, jstring value)
{
    const char* utf_chars;
    char* duplicated;

    if (value == NULL)
    {
        return MediaSfuUnityDuplicateString("");
    }

    utf_chars = (*env)->GetStringUTFChars(env, value, NULL);
    if (utf_chars == NULL)
    {
        media_sfu_android_clear_exception(env);
        return NULL;
    }

    duplicated = MediaSfuUnityDuplicateString(utf_chars);
    (*env)->ReleaseStringUTFChars(env, value, utf_chars);
    return duplicated;
}

static jstring media_sfu_android_new_string(JNIEnv* env, const char* value)
{
    return (*env)->NewStringUTF(env, value != NULL ? value : "");
}

static void* media_sfu_android_backend_create_engine(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut)
{
    JNIEnv* env = NULL;
    int did_attach = 0;
    jstring payload = NULL;
    jstring error_detail = NULL;
    jlong handle;

    (void)installContext;

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    if (!media_sfu_android_get_env(&env, &did_attach) || g_bridge_class == NULL || g_create_engine_method == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString(
                "MediaSFU Android JNI backend could not access the Kotlin bridge class.");
        }
        return NULL;
    }

    payload = media_sfu_android_new_string(env, createPayloadJson);
    handle = (*env)->CallStaticLongMethod(env, g_bridge_class, g_create_engine_method, payload);

    if (payload != NULL)
    {
        (*env)->DeleteLocalRef(env, payload);
    }

    if ((*env)->ExceptionCheck(env))
    {
        media_sfu_android_clear_exception(env);
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString(
                "MediaSFU Android JNI backend hit a Java exception while creating the Kotlin Unity WebRTC engine.");
        }
        media_sfu_android_release_env(did_attach);
        return NULL;
    }

    if (handle == 0)
    {
        if (errorDetailOut != NULL && g_consume_create_engine_error_method != NULL)
        {
            error_detail = (jstring)(*env)->CallStaticObjectMethod(
                env,
                g_bridge_class,
                g_consume_create_engine_error_method);

            if (!(*env)->ExceptionCheck(env))
            {
                *errorDetailOut = media_sfu_android_duplicate_jstring(env, error_detail);
            }
            else
            {
                media_sfu_android_clear_exception(env);
            }

            if (error_detail != NULL)
            {
                (*env)->DeleteLocalRef(env, error_detail);
            }
        }

        if (errorDetailOut != NULL && (*errorDetailOut == NULL || (*errorDetailOut)[0] == '\0'))
        {
            *errorDetailOut = MediaSfuUnityDuplicateString(
                "MediaSFU Android JNI backend failed to create the Kotlin Unity WebRTC engine.");
        }

        media_sfu_android_release_env(did_attach);
        return NULL;
    }

    media_sfu_android_release_env(did_attach);
    return (void*)(intptr_t)handle;
}

static void media_sfu_android_backend_destroy_engine(
    void* installContext,
    void* backendEngine)
{
    JNIEnv* env = NULL;
    int did_attach = 0;

    (void)installContext;

    if (backendEngine == NULL)
    {
        return;
    }

    if (!media_sfu_android_get_env(&env, &did_attach) || g_bridge_class == NULL || g_destroy_engine_method == NULL)
    {
        return;
    }

    (*env)->CallStaticVoidMethod(
        env,
        g_bridge_class,
        g_destroy_engine_method,
        (jlong)(intptr_t)backendEngine);

    media_sfu_android_clear_exception(env);
    media_sfu_android_release_env(did_attach);
}

static char* media_sfu_android_backend_invoke(
    void* installContext,
    void* backendEngine,
    const char* operationName,
    const char* payloadJson)
{
    JNIEnv* env = NULL;
    int did_attach = 0;
    jstring operation_name = NULL;
    jstring payload = NULL;
    jstring response = NULL;
    char* duplicated_response = NULL;

    (void)installContext;

    if (!media_sfu_android_get_env(&env, &did_attach) || g_bridge_class == NULL || g_invoke_method == NULL)
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_missing_backend",
            "MediaSFU Android JNI backend could not access the Kotlin bridge class.");
    }

    operation_name = media_sfu_android_new_string(env, operationName);
    payload = media_sfu_android_new_string(env, payloadJson);

    response = (jstring)(*env)->CallStaticObjectMethod(
        env,
        g_bridge_class,
        g_invoke_method,
        (jlong)(intptr_t)backendEngine,
        operation_name,
        payload);

    if (!(*env)->ExceptionCheck(env))
    {
        duplicated_response = media_sfu_android_duplicate_jstring(env, response);
    }
    else
    {
        media_sfu_android_clear_exception(env);
    }

    if (response != NULL)
    {
        (*env)->DeleteLocalRef(env, response);
    }
    if (operation_name != NULL)
    {
        (*env)->DeleteLocalRef(env, operation_name);
    }
    if (payload != NULL)
    {
        (*env)->DeleteLocalRef(env, payload);
    }

    media_sfu_android_release_env(did_attach);

    if (duplicated_response == NULL)
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_no_response",
            "MediaSFU Android JNI backend did not return a response from the Kotlin bridge.");
    }

    return duplicated_response;
}

static const char* media_sfu_android_backend_describe(
    void* installContext)
{
    (void)installContext;
    return "MediaSFU Android JNI backend";
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jclass local_bridge_class = NULL;

    (void)reserved;

    g_media_sfu_java_vm = vm;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK)
    {
        return JNI_VERSION_1_6;
    }

    local_bridge_class = (*env)->FindClass(env, "com/mediasfu/sdk/unity/AndroidUnityWebRtcJniBridge");
    if (local_bridge_class == NULL)
    {
        media_sfu_android_clear_exception(env);
        return JNI_VERSION_1_6;
    }

    g_bridge_class = (*env)->NewGlobalRef(env, local_bridge_class);
    (*env)->DeleteLocalRef(env, local_bridge_class);

    if (g_bridge_class == NULL)
    {
        return JNI_VERSION_1_6;
    }

    g_create_engine_method = (*env)->GetStaticMethodID(env, g_bridge_class, "createEngine", "(Ljava/lang/String;)J");
    g_destroy_engine_method = (*env)->GetStaticMethodID(env, g_bridge_class, "destroyEngine", "(J)V");
    g_invoke_method = (*env)->GetStaticMethodID(env, g_bridge_class, "invoke", "(JLjava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    g_consume_create_engine_error_method = (*env)->GetStaticMethodID(env, g_bridge_class, "consumeLastCreateEngineError", "()Ljava/lang/String;");

    if (g_create_engine_method == NULL ||
        g_destroy_engine_method == NULL ||
        g_invoke_method == NULL ||
        g_consume_create_engine_error_method == NULL)
    {
        media_sfu_android_clear_exception(env);
        if (g_bridge_class != NULL)
        {
            (*env)->DeleteGlobalRef(env, g_bridge_class);
            g_bridge_class = NULL;
        }
        return JNI_VERSION_1_6;
    }

    MediaSfuUnityInstallWebRtcBackend(&MEDIA_SFU_ANDROID_JNI_BACKEND, NULL);
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;

    (void)reserved;

    MediaSfuUnityResetWebRtcBackend();

    if (vm == NULL)
    {
        return;
    }

    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK && g_bridge_class != NULL)
    {
        (*env)->DeleteGlobalRef(env, g_bridge_class);
    }

    g_bridge_class = NULL;
    g_create_engine_method = NULL;
    g_destroy_engine_method = NULL;
    g_invoke_method = NULL;
    g_consume_create_engine_error_method = NULL;
    g_media_sfu_java_vm = NULL;
}

#endif