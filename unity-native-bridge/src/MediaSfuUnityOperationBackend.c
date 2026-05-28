#include "MediaSfuUnityOperationBackend.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct MediaSfuUnityOperationBackendInstallState
{
    const MediaSfuUnityOperationBackend* backend;
    void* install_context;
} MediaSfuUnityOperationBackendInstallState;

static void* media_sfu_operation_backend_create_engine(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut);

static void media_sfu_operation_backend_destroy_engine(
    void* installContext,
    void* backendEngine);

static char* media_sfu_operation_backend_invoke(
    void* installContext,
    void* backendEngine,
    const char* operationName,
    const char* payloadJson);

static const char* media_sfu_operation_backend_describe(
    void* installContext);

static const MediaSfuUnityWebRtcBackend MEDIA_SFU_OPERATION_WRAPPER_BACKEND =
{
    media_sfu_operation_backend_create_engine,
    media_sfu_operation_backend_destroy_engine,
    media_sfu_operation_backend_invoke,
    media_sfu_operation_backend_describe
};

static MediaSfuUnityOperationBackendInstallState g_operation_backend_install_state = { NULL, NULL };

static const char* MEDIA_SFU_OPERATION_LOAD_DEVICE_RTP_CAPABILITIES = "loadDeviceRtpCapabilities";
static const char* MEDIA_SFU_OPERATION_INITIALIZE_SEND_TRANSPORT = "initializeSendTransport";
static const char* MEDIA_SFU_OPERATION_CREATE_SEND_TRANSPORT_CONNECT_PARAMETERS = "createSendTransportConnectParameters";
static const char* MEDIA_SFU_OPERATION_COMPLETE_SEND_TRANSPORT_CONNECT = "completeSendTransportConnect";
static const char* MEDIA_SFU_OPERATION_CREATE_PRODUCE_REQUEST = "createProduceRequest";
static const char* MEDIA_SFU_OPERATION_BIND_PRODUCER = "bindProducer";
static const char* MEDIA_SFU_OPERATION_PAUSE_PRODUCER = "pauseProducer";
static const char* MEDIA_SFU_OPERATION_RESUME_PRODUCER = "resumeProducer";
static const char* MEDIA_SFU_OPERATION_CLOSE_PRODUCER = "closeProducer";
static const char* MEDIA_SFU_OPERATION_INITIALIZE_RECEIVE_TRANSPORT = "initializeReceiveTransport";
static const char* MEDIA_SFU_OPERATION_CREATE_RECEIVE_TRANSPORT_CONNECT_PARAMETERS = "createReceiveTransportConnectParameters";
static const char* MEDIA_SFU_OPERATION_COMPLETE_RECEIVE_TRANSPORT_CONNECT = "completeReceiveTransportConnect";
static const char* MEDIA_SFU_OPERATION_BIND_CONSUMER = "bindConsumer";
static const char* MEDIA_SFU_OPERATION_CLOSE_CONSUMER = "closeConsumer";

static const MediaSfuUnityOperationBackendInstallState* media_sfu_as_operation_install_state(
    void* installContext)
{
    return (const MediaSfuUnityOperationBackendInstallState*)installContext;
}

static const char* media_sfu_operation_backend_description_for_state(
    const MediaSfuUnityOperationBackendInstallState* state)
{
    if (state != NULL &&
        state->backend != NULL &&
        state->backend->describe != NULL)
    {
        const char* description = state->backend->describe(state->install_context);
        if (description != NULL && description[0] != '\0')
        {
            return description;
        }
    }

    return "MediaSFU Unity operation backend";
}

static char* media_sfu_operation_backend_missing_callback_response(
    const MediaSfuUnityOperationBackendInstallState* state,
    const char* operationName)
{
    char detail[512];

    snprintf(
        detail,
        sizeof(detail),
        "%s does not implement operation %s.",
        media_sfu_operation_backend_description_for_state(state),
        operationName != NULL ? operationName : "<null>");

    return MediaSfuUnityCreateFailureResponse(
        "native_bridge_unimplemented_operation",
        detail);
}

static char* media_sfu_operation_backend_failed_response(
    const char* operationName,
    char* errorDetail)
{
    char detail[512];
    char* response;

    if (errorDetail != NULL && errorDetail[0] != '\0')
    {
        response = MediaSfuUnityCreateFailureResponse(
            "native_bridge_operation_failed",
            errorDetail);
        free(errorDetail);
        return response;
    }

    snprintf(
        detail,
        sizeof(detail),
        "MediaSFU Unity operation backend failed while handling %s.",
        operationName != NULL ? operationName : "<null>");

    free(errorDetail);
    return MediaSfuUnityCreateFailureResponse(
        "native_bridge_operation_failed",
        detail);
}

static char* media_sfu_operation_backend_invoke_void(
    const MediaSfuUnityOperationBackendInstallState* state,
    void* backendEngine,
    MediaSfuUnityOperationBackendVoidOperationFn operation,
    const char* operationName,
    const char* payloadJson)
{
    char* error_detail = NULL;
    int success;

    if (operation == NULL)
    {
        return media_sfu_operation_backend_missing_callback_response(state, operationName);
    }

    success = operation(
        state->install_context,
        backendEngine,
        payloadJson,
        &error_detail);

    if (!success)
    {
        return media_sfu_operation_backend_failed_response(operationName, error_detail);
    }

    free(error_detail);
    return MediaSfuUnityCreateSuccessResponse("null");
}

static char* media_sfu_operation_backend_invoke_json(
    const MediaSfuUnityOperationBackendInstallState* state,
    void* backendEngine,
    MediaSfuUnityOperationBackendJsonOperationFn operation,
    const char* operationName,
    const char* payloadJson)
{
    char* error_detail = NULL;
    char* result_json;
    char* response;

    if (operation == NULL)
    {
        return media_sfu_operation_backend_missing_callback_response(state, operationName);
    }

    result_json = operation(
        state->install_context,
        backendEngine,
        payloadJson,
        &error_detail);

    if (result_json == NULL)
    {
        return media_sfu_operation_backend_failed_response(operationName, error_detail);
    }

    response = MediaSfuUnityCreateSuccessResponse(result_json);
    free(error_detail);
    free(result_json);
    return response;
}

MEDIASFU_UNITY_BRIDGE_API void MediaSfuUnityInstallOperationBackend(
    const MediaSfuUnityOperationBackend* backend,
    void* installContext)
{
    g_operation_backend_install_state.backend = backend;
    g_operation_backend_install_state.install_context = installContext;

    if (backend == NULL ||
        backend->create_engine == NULL ||
        backend->destroy_engine == NULL)
    {
        MediaSfuUnityResetWebRtcBackend();
        return;
    }

    MediaSfuUnityInstallWebRtcBackend(
        &MEDIA_SFU_OPERATION_WRAPPER_BACKEND,
        &g_operation_backend_install_state);
}

static void* media_sfu_operation_backend_create_engine(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut)
{
    const MediaSfuUnityOperationBackendInstallState* state = media_sfu_as_operation_install_state(installContext);

    if (errorDetailOut != NULL)
    {
        *errorDetailOut = NULL;
    }

    if (state == NULL ||
        state->backend == NULL ||
        state->backend->create_engine == NULL)
    {
        if (errorDetailOut != NULL)
        {
            *errorDetailOut = MediaSfuUnityDuplicateString(
                "MediaSFU Unity operation backend is missing the create_engine callback.");
        }

        return NULL;
    }

    return state->backend->create_engine(
        state->install_context,
        createPayloadJson,
        errorDetailOut);
}

static void media_sfu_operation_backend_destroy_engine(
    void* installContext,
    void* backendEngine)
{
    const MediaSfuUnityOperationBackendInstallState* state = media_sfu_as_operation_install_state(installContext);

    if (state == NULL ||
        state->backend == NULL ||
        state->backend->destroy_engine == NULL)
    {
        return;
    }

    state->backend->destroy_engine(state->install_context, backendEngine);
}

static char* media_sfu_operation_backend_invoke(
    void* installContext,
    void* backendEngine,
    const char* operationName,
    const char* payloadJson)
{
    const MediaSfuUnityOperationBackendInstallState* state = media_sfu_as_operation_install_state(installContext);
    char detail[512];

    if (state == NULL || state->backend == NULL)
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_missing_backend",
            "MediaSFU Unity operation backend was not installed correctly.");
    }

    if (operationName == NULL || operationName[0] == '\0')
    {
        return MediaSfuUnityCreateFailureResponse(
            "native_bridge_invalid_operation",
            "MediaSFU Unity operation backend received an empty operation name.");
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_LOAD_DEVICE_RTP_CAPABILITIES) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->load_device_rtp_capabilities,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_INITIALIZE_SEND_TRANSPORT) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->initialize_send_transport,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_CREATE_SEND_TRANSPORT_CONNECT_PARAMETERS) == 0)
    {
        return media_sfu_operation_backend_invoke_json(
            state,
            backendEngine,
            state->backend->create_send_transport_connect_parameters,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_COMPLETE_SEND_TRANSPORT_CONNECT) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->complete_send_transport_connect,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_CREATE_PRODUCE_REQUEST) == 0)
    {
        return media_sfu_operation_backend_invoke_json(
            state,
            backendEngine,
            state->backend->create_produce_request,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_BIND_PRODUCER) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->bind_producer,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_PAUSE_PRODUCER) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->pause_producer,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_RESUME_PRODUCER) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->resume_producer,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_CLOSE_PRODUCER) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->close_producer,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_INITIALIZE_RECEIVE_TRANSPORT) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->initialize_receive_transport,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_CREATE_RECEIVE_TRANSPORT_CONNECT_PARAMETERS) == 0)
    {
        return media_sfu_operation_backend_invoke_json(
            state,
            backendEngine,
            state->backend->create_receive_transport_connect_parameters,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_COMPLETE_RECEIVE_TRANSPORT_CONNECT) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->complete_receive_transport_connect,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_BIND_CONSUMER) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->bind_consumer,
            operationName,
            payloadJson);
    }

    if (strcmp(operationName, MEDIA_SFU_OPERATION_CLOSE_CONSUMER) == 0)
    {
        return media_sfu_operation_backend_invoke_void(
            state,
            backendEngine,
            state->backend->close_consumer,
            operationName,
            payloadJson);
    }

    snprintf(
        detail,
        sizeof(detail),
        "%s does not recognize operation %s.",
        media_sfu_operation_backend_description_for_state(state),
        operationName);

    return MediaSfuUnityCreateFailureResponse(
        "native_bridge_unknown_operation",
        detail);
}

static const char* media_sfu_operation_backend_describe(
    void* installContext)
{
    return media_sfu_operation_backend_description_for_state(
        media_sfu_as_operation_install_state(installContext));
}