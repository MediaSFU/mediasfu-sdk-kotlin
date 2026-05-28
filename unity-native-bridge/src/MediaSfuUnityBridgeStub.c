#include "MediaSfuUnityBridge.h"
#include "MediaSfuUnityBridgeBackend.h"

#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct MediaSfuUnityEngineState
{
    const MediaSfuUnityWebRtcBackend* backend;
    void* install_context;
    void* backend_engine;
    char* create_payload_json;
} MediaSfuUnityEngineState;

typedef struct MediaSfuUnityInstalledBackendState
{
    const MediaSfuUnityWebRtcBackend* backend;
    void* install_context;
} MediaSfuUnityInstalledBackendState;

static void* media_sfu_placeholder_create_engine(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut);

static void media_sfu_placeholder_destroy_engine(
    void* installContext,
    void* backendEngine);

static char* media_sfu_placeholder_invoke(
    void* installContext,
    void* backendEngine,
    const char* operationName,
    const char* payloadJson);

static const char* media_sfu_placeholder_describe(
    void* installContext);

static const MediaSfuUnityWebRtcBackend MEDIA_SFU_PLACEHOLDER_BACKEND =
{
    media_sfu_placeholder_create_engine,
    media_sfu_placeholder_destroy_engine,
    media_sfu_placeholder_invoke,
    media_sfu_placeholder_describe
};

static MediaSfuUnityInstalledBackendState g_installed_backend = { NULL, NULL };

static size_t media_sfu_json_escaped_length(const char* source)
{
    size_t length = 0u;

    if (source == NULL)
    {
        return 0u;
    }

    while (*source != '\0')
    {
        switch (*source)
        {
            case '\\':
            case '"':
            case '\n':
            case '\r':
            case '\t':
                length += 2u;
                break;
            default:
                length += 1u;
                break;
        }

        source += 1;
    }

    return length;
}

static char* media_sfu_strdup(const char* source)
{
    size_t length;
    char* copy;

    if (source == NULL)
    {
        source = "";
    }

    length = strlen(source);
    copy = (char*)malloc(length + 1u);
    if (copy == NULL)
    {
        return NULL;
    }

    memcpy(copy, source, length + 1u);
    return copy;
}

static char* media_sfu_json_escape(const char* source)
{
    char* escaped;
    char* cursor;

    if (source == NULL)
    {
        return media_sfu_strdup("");
    }

    escaped = (char*)malloc(media_sfu_json_escaped_length(source) + 1u);
    if (escaped == NULL)
    {
        return NULL;
    }

    cursor = escaped;
    while (*source != '\0')
    {
        switch (*source)
        {
            case '\\':
                *cursor++ = '\\';
                *cursor++ = '\\';
                break;
            case '"':
                *cursor++ = '\\';
                *cursor++ = '"';
                break;
            case '\n':
                *cursor++ = '\\';
                *cursor++ = 'n';
                break;
            case '\r':
                *cursor++ = '\\';
                *cursor++ = 'r';
                break;
            case '\t':
                *cursor++ = '\\';
                *cursor++ = 't';
                break;
            default:
                *cursor++ = *source;
                break;
        }

        source += 1;
    }

    *cursor = '\0';
    return escaped;
}

static char* media_sfu_build_response(
    int success,
    const char* errorCode,
    const char* detail,
    const char* resultJson)
{
    const char* prefix = success
        ? "{\"success\":true,\"error\":\""
        : "{\"success\":false,\"error\":\"";
    const char* middle = "\",\"detail\":\"";
    const char* suffix = "\",\"result\":";
    const char* fallback_result = "null";
    char* escaped_error = media_sfu_json_escape(errorCode);
    char* escaped_detail = media_sfu_json_escape(detail);
    const char* result = (resultJson == NULL || resultJson[0] == '\0')
        ? fallback_result
        : resultJson;
    size_t prefix_length = strlen(prefix);
    size_t escaped_error_length;
    size_t middle_length = strlen(middle);
    size_t escaped_detail_length;
    size_t suffix_length = strlen(suffix);
    size_t result_length = strlen(result);
    char* response;
    char* cursor;

    if (escaped_error == NULL || escaped_detail == NULL)
    {
        free(escaped_error);
        free(escaped_detail);
        return NULL;
    }

    escaped_error_length = strlen(escaped_error);
    escaped_detail_length = strlen(escaped_detail);
    response = (char*)malloc(
        prefix_length +
        escaped_error_length +
        middle_length +
        escaped_detail_length +
        suffix_length +
        result_length +
        2u);

    if (response == NULL)
    {
        free(escaped_error);
        free(escaped_detail);
        return NULL;
    }

    cursor = response;
    memcpy(cursor, prefix, prefix_length);
    cursor += prefix_length;
    memcpy(cursor, escaped_error, escaped_error_length);
    cursor += escaped_error_length;
    memcpy(cursor, middle, middle_length);
    cursor += middle_length;
    memcpy(cursor, escaped_detail, escaped_detail_length);
    cursor += escaped_detail_length;
    memcpy(cursor, suffix, suffix_length);
    cursor += suffix_length;
    memcpy(cursor, result, result_length);
    cursor += result_length;
    *cursor++ = '}';
    *cursor = '\0';

    free(escaped_error);
    free(escaped_detail);
    return response;
}

MEDIASFU_UNITY_BRIDGE_API void MediaSfuUnityInstallWebRtcBackend(
    const MediaSfuUnityWebRtcBackend* backend,
    void* installContext)
{
    if (backend == NULL ||
        backend->create_engine == NULL ||
        backend->destroy_engine == NULL ||
        backend->invoke == NULL)
    {
        g_installed_backend.backend = NULL;
        g_installed_backend.install_context = NULL;
        return;
    }

    g_installed_backend.backend = backend;
    g_installed_backend.install_context = installContext;
}

MEDIASFU_UNITY_BRIDGE_API void MediaSfuUnityResetWebRtcBackend(void)
{
    g_installed_backend.backend = NULL;
    g_installed_backend.install_context = NULL;
}

MEDIASFU_UNITY_BRIDGE_API char* MediaSfuUnityDuplicateString(const char* value)
{
    return media_sfu_strdup(value);
}

MEDIASFU_UNITY_BRIDGE_API char* MediaSfuUnityCreateFailureResponse(
    const char* errorCode,
    const char* detail)
{
    return media_sfu_build_response(0, errorCode, detail, "null");
}

MEDIASFU_UNITY_BRIDGE_API char* MediaSfuUnityCreateSuccessResponse(
    const char* resultJson)
{
    return media_sfu_build_response(1, "", "", resultJson);
}

static const MediaSfuUnityWebRtcBackend* media_sfu_active_backend(void)
{
    return g_installed_backend.backend != NULL
        ? g_installed_backend.backend
        : &MEDIA_SFU_PLACEHOLDER_BACKEND;
}

static char* media_sfu_build_backend_info_response(const MediaSfuUnityEngineState* state)
{
    const char* description;
    const char* backend_kind;
    int is_placeholder;
    char* escaped_description;
    char result_json[768];

    if (state == NULL || state->backend == NULL)
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_invalid_engine",
            "MediaSFU Unity native bridge cannot describe a null engine state.");
    }

    description = state->backend->describe != NULL
        ? state->backend->describe(state->install_context)
        : "MediaSFU Unity native bridge";
    is_placeholder = state->backend == &MEDIA_SFU_PLACEHOLDER_BACKEND;
    backend_kind = is_placeholder ? "placeholder" : "installed";
    escaped_description = media_sfu_json_escape(description);
    if (escaped_description == NULL)
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_out_of_memory",
            "MediaSFU Unity native bridge could not allocate the backend description response.");
    }

    snprintf(
        result_json,
        sizeof(result_json),
        "{\"description\":\"%s\",\"backendKind\":\"%s\",\"isPlaceholder\":%s}",
        escaped_description,
        backend_kind,
        is_placeholder ? "true" : "false");

    free(escaped_description);
    return MediaSfuUnityCreateSuccessResponse(result_json);
}

static int media_sfu_response_is_success(const char* response_json)
{
    return response_json != NULL && strstr(response_json, "\"success\":true") != NULL;
}

static void* media_sfu_active_install_context(void)
{
    return g_installed_backend.backend != NULL
        ? g_installed_backend.install_context
        : NULL;
}

void* MediaSfuUnityCreateWebRtcEngine(const char* payloadJson)
{
    MediaSfuUnityEngineState* state = (MediaSfuUnityEngineState*)calloc(1u, sizeof(MediaSfuUnityEngineState));
    const MediaSfuUnityWebRtcBackend* backend = media_sfu_active_backend();
    void* install_context = media_sfu_active_install_context();
    char* error_detail = NULL;

    if (state == NULL)
    {
        return NULL;
    }

    state->create_payload_json = media_sfu_strdup(payloadJson);
    if (state->create_payload_json == NULL)
    {
        free(state);
        return NULL;
    }

    state->backend = backend;
    state->install_context = install_context;
    state->backend_engine = backend->create_engine(install_context, payloadJson, &error_detail);
    if (state->backend_engine == NULL)
    {
        if (error_detail != NULL && error_detail[0] != '\0')
        {
            fprintf(stderr, "MediaSFU Unity native bridge failed to create engine: %s\n", error_detail);
        }

        free(error_detail);
        free(state->create_payload_json);
        free(state);
        return NULL;
    }

    return state;
}

void MediaSfuUnityDestroyWebRtcEngine(void* engineHandle)
{
    MediaSfuUnityEngineState* state = (MediaSfuUnityEngineState*)engineHandle;
    if (state == NULL)
    {
        return;
    }

    if (state->backend != NULL && state->backend->destroy_engine != NULL)
    {
        state->backend->destroy_engine(state->install_context, state->backend_engine);
    }

    free(state->create_payload_json);
    free(state);
}

char* MediaSfuUnityInvokeWebRtcEngine(void* engineHandle, const char* operationName, const char* payloadJson)
{
    MediaSfuUnityEngineState* state = (MediaSfuUnityEngineState*)engineHandle;
    char* response;

    if (state == NULL)
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_invalid_engine",
            "MediaSFU Unity native bridge placeholder received a null engine handle. Create the engine before invoking WebRTC operations.");
    }

    if (operationName == NULL || operationName[0] == '\0')
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_invalid_operation",
            "MediaSFU Unity native bridge placeholder received an empty operation name. Replace the stub bridge with a real Android or iOS implementation.");
    }

    if (strcmp(operationName, "describeBackend") == 0)
    {
        if (state->backend != NULL &&
            state->backend != &MEDIA_SFU_PLACEHOLDER_BACKEND &&
            state->backend->invoke != NULL)
        {
            response = state->backend->invoke(
                state->install_context,
                state->backend_engine,
                operationName,
                payloadJson);

            if (media_sfu_response_is_success(response))
            {
                return response;
            }

            free(response);
        }

        return media_sfu_build_backend_info_response(state);
    }

    response = state->backend->invoke(
        state->install_context,
        state->backend_engine,
        operationName,
        payloadJson);
    if (response == NULL)
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_no_response",
            "MediaSFU Unity native bridge backend returned no response payload.");
    }

    return response;
}

void MediaSfuUnityFreeString(char* responsePointer)
{
    free(responsePointer);
}

static void* media_sfu_placeholder_create_engine(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut)
{
    (void)installContext;
    (void)createPayloadJson;

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    return (void*)0x1;
}

static void media_sfu_placeholder_destroy_engine(
    void* installContext,
    void* backendEngine)
{
    (void)installContext;
    (void)backendEngine;
}

static char* media_sfu_placeholder_invoke(
    void* installContext,
    void* backendEngine,
    const char* operationName,
    const char* payloadJson)
{
    char detail[768];
    const char* backend_description = media_sfu_placeholder_describe(installContext);

    (void)backendEngine;
    (void)payloadJson;

    snprintf(
        detail,
        sizeof(detail),
        "%s cannot execute operation %s. Replace unity-native-bridge/src/MediaSfuUnityBridgeStub.c with a real Android or iOS bridge, or call MediaSfuUnityInstallWebRtcBackend(...) from a native backend package.",
        backend_description,
        operationName);

    return MediaSfuUnityCreateFailureResponse("placeholder_native_bridge", detail);
}

static const char* media_sfu_placeholder_describe(
    void* installContext)
{
    (void)installContext;
    return "MediaSFU Unity native bridge placeholder";
}
