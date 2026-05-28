#include "MediaSfuUnityOperationBackend.h"

#include <assert.h>
#include <stdlib.h>
#include <string.h>

typedef struct MediaSfuUnitySmokeTestEngine
{
    char* create_payload_json;
    int bind_producer_calls;
} MediaSfuUnitySmokeTestEngine;

static void* media_sfu_smoke_test_create_engine(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut)
{
    MediaSfuUnitySmokeTestEngine* engine;

    (void)installContext;

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    engine = (MediaSfuUnitySmokeTestEngine*)calloc(1u, sizeof(MediaSfuUnitySmokeTestEngine));
    if (engine == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Out of memory while creating smoke test engine.");
        }

        return NULL;
    }

    engine->create_payload_json = MediaSfuUnityDuplicateString(createPayloadJson != NULL ? createPayloadJson : "");
    if (engine->create_payload_json == NULL)
    {
        free(engine);
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Out of memory while storing smoke test create payload.");
        }

        return NULL;
    }

    return engine;
}

static void media_sfu_smoke_test_destroy_engine(
    void* installContext,
    void* backendEngine)
{
    MediaSfuUnitySmokeTestEngine* engine = (MediaSfuUnitySmokeTestEngine*)backendEngine;

    (void)installContext;

    if (engine == NULL)
    {
        return;
    }

    free(engine->create_payload_json);
    free(engine);
}

static int media_sfu_smoke_test_load_device_rtp_capabilities(
    void* installContext,
    void* backendEngine,
    const char* payloadJson,
    char** errorDetailOut)
{
    MediaSfuUnitySmokeTestEngine* engine = (MediaSfuUnitySmokeTestEngine*)backendEngine;

    (void)installContext;

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    if (engine == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test engine was null during loadDeviceRtpCapabilities.");
        }

        return 0;
    }

    if (payloadJson == NULL || strstr(payloadJson, "roomRtpCapabilitiesJson") == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test loadDeviceRtpCapabilities payload was missing roomRtpCapabilitiesJson.");
        }

        return 0;
    }

    return 1;
}

static int media_sfu_smoke_test_complete_send_transport_connect(
    void* installContext,
    void* backendEngine,
    const char* payloadJson,
    char** errorDetailOut)
{
    MediaSfuUnitySmokeTestEngine* engine = (MediaSfuUnitySmokeTestEngine*)backendEngine;

    (void)installContext;

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    if (engine == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test engine was null during completeSendTransportConnect.");
        }

        return 0;
    }

    if (payloadJson == NULL || strstr(payloadJson, "success") == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test completeSendTransportConnect payload was missing success.");
        }

        return 0;
    }

    return 1;
}

static int media_sfu_smoke_test_bind_producer(
    void* installContext,
    void* backendEngine,
    const char* payloadJson,
    char** errorDetailOut)
{
    MediaSfuUnitySmokeTestEngine* engine = (MediaSfuUnitySmokeTestEngine*)backendEngine;

    (void)installContext;

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    if (engine == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test engine was null during bindProducer.");
        }

        return 0;
    }

    if (payloadJson == NULL || strstr(payloadJson, "producerId") == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test bindProducer payload was missing producerId.");
        }

        return 0;
    }

    engine->bind_producer_calls += 1;
    return 1;
}

static char* media_sfu_smoke_test_create_send_transport_connect_parameters(
    void* installContext,
    void* backendEngine,
    const char* payloadJson,
    char** errorDetailOut)
{
    MediaSfuUnitySmokeTestEngine* engine = (MediaSfuUnitySmokeTestEngine*)backendEngine;

    (void)installContext;
    (void)payloadJson;

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    if (engine == NULL || engine->create_payload_json == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test engine was not initialized before createSendTransportConnectParameters.");
        }

        return NULL;
    }

    return MediaSfuUnityDuplicateString(
        "{\"dtlsParametersJson\":\"{\\\"role\\\":\\\"auto\\\"}\",\"source\":\"operation-backend-smoke-test\"}");
}

static int media_sfu_smoke_test_complete_receive_transport_connect(
    void* installContext,
    void* backendEngine,
    const char* payloadJson,
    char** errorDetailOut)
{
    MediaSfuUnitySmokeTestEngine* engine = (MediaSfuUnitySmokeTestEngine*)backendEngine;

    (void)installContext;

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    if (engine == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test engine was null during completeReceiveTransportConnect.");
        }

        return 0;
    }

    if (payloadJson == NULL ||
        strstr(payloadJson, "success") == NULL ||
        strstr(payloadJson, "remoteProducer") == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString("Smoke test completeReceiveTransportConnect payload was missing success or remoteProducer.");
        }

        return 0;
    }

    return 1;
}

static const char* media_sfu_smoke_test_describe(
    void* installContext)
{
    (void)installContext;
    return "MediaSFU Unity operation backend smoke test";
}

static const MediaSfuUnityOperationBackend MEDIA_SFU_SMOKE_TEST_BACKEND =
{
    media_sfu_smoke_test_create_engine,
    media_sfu_smoke_test_destroy_engine,
    media_sfu_smoke_test_load_device_rtp_capabilities,
    NULL,
    media_sfu_smoke_test_create_send_transport_connect_parameters,
    media_sfu_smoke_test_complete_send_transport_connect,
    NULL,
    media_sfu_smoke_test_bind_producer,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    media_sfu_smoke_test_complete_receive_transport_connect,
    NULL,
    NULL,
    media_sfu_smoke_test_describe
};

static void media_sfu_assert_response_contains(
    const char* responseJson,
    const char* expectedFragment)
{
    assert(responseJson != NULL);
    assert(expectedFragment != NULL);
    assert(strstr(responseJson, expectedFragment) != NULL);
}

int main(void)
{
    void* engine;
    char* response_json;

    MediaSfuUnityInstallOperationBackend(&MEDIA_SFU_SMOKE_TEST_BACKEND, NULL);

    engine = MediaSfuUnityCreateWebRtcEngine("{\"integrationMode\":\"smoke-test\"}");
    assert(engine != NULL);

    response_json = MediaSfuUnityInvokeWebRtcEngine(
        engine,
        "loadDeviceRtpCapabilities",
        "{\"roomRtpCapabilitiesJson\":\"{}\"}");
    media_sfu_assert_response_contains(response_json, "\"success\":true");
    media_sfu_assert_response_contains(response_json, "\"result\":null");
    MediaSfuUnityFreeString(response_json);

    response_json = MediaSfuUnityInvokeWebRtcEngine(
        engine,
        "createSendTransportConnectParameters",
        "{}");
    media_sfu_assert_response_contains(response_json, "\"success\":true");
    media_sfu_assert_response_contains(response_json, "operation-backend-smoke-test");
    MediaSfuUnityFreeString(response_json);

    response_json = MediaSfuUnityInvokeWebRtcEngine(
        engine,
        "completeSendTransportConnect",
        "{\"success\":true,\"errorDetail\":\"\"}");
    media_sfu_assert_response_contains(response_json, "\"success\":true");
    media_sfu_assert_response_contains(response_json, "\"result\":null");
    MediaSfuUnityFreeString(response_json);

    response_json = MediaSfuUnityInvokeWebRtcEngine(
        engine,
        "bindProducer",
        "{\"trackKind\":1,\"producerId\":\"producer-1\"}");
    media_sfu_assert_response_contains(response_json, "\"success\":true");
    media_sfu_assert_response_contains(response_json, "\"result\":null");
    MediaSfuUnityFreeString(response_json);

    response_json = MediaSfuUnityInvokeWebRtcEngine(
        engine,
        "completeReceiveTransportConnect",
        "{\"remoteProducer\":{\"producerId\":\"producer-1\"},\"success\":true,\"errorDetail\":\"\"}");
    media_sfu_assert_response_contains(response_json, "\"success\":true");
    media_sfu_assert_response_contains(response_json, "\"result\":null");
    MediaSfuUnityFreeString(response_json);

    response_json = MediaSfuUnityInvokeWebRtcEngine(
        engine,
        "bindConsumer",
        "{}");
    media_sfu_assert_response_contains(response_json, "native_bridge_unimplemented_operation");
    media_sfu_assert_response_contains(response_json, "MediaSFU Unity operation backend smoke test");
    MediaSfuUnityFreeString(response_json);

    response_json = MediaSfuUnityInvokeWebRtcEngine(
        engine,
        "unknownOperation",
        "{}");
    media_sfu_assert_response_contains(response_json, "native_bridge_unknown_operation");
    MediaSfuUnityFreeString(response_json);

    MediaSfuUnityDestroyWebRtcEngine(engine);
    MediaSfuUnityResetWebRtcBackend();
    return 0;
}