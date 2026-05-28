#ifndef MEDIASFU_UNITY_OPERATION_BACKEND_H
#define MEDIASFU_UNITY_OPERATION_BACKEND_H

#include "MediaSfuUnityBridgeBackend.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef void* (*MediaSfuUnityOperationBackendCreateEngineFn)(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut);

typedef void (*MediaSfuUnityOperationBackendDestroyEngineFn)(
    void* installContext,
    void* backendEngine);

typedef int (*MediaSfuUnityOperationBackendVoidOperationFn)(
    void* installContext,
    void* backendEngine,
    const char* payloadJson,
    char** errorDetailOut);

/*
 * Result callbacks must return a heap-allocated UTF-8 JSON object string.
 * MediaSfuUnityDuplicateString(...) is the easiest way to allocate small results.
 */
typedef char* (*MediaSfuUnityOperationBackendJsonOperationFn)(
    void* installContext,
    void* backendEngine,
    const char* payloadJson,
    char** errorDetailOut);

typedef const char* (*MediaSfuUnityOperationBackendDescribeFn)(
    void* installContext);

typedef struct MediaSfuUnityOperationBackend
{
    MediaSfuUnityOperationBackendCreateEngineFn create_engine;
    MediaSfuUnityOperationBackendDestroyEngineFn destroy_engine;
    MediaSfuUnityOperationBackendVoidOperationFn load_device_rtp_capabilities;
    MediaSfuUnityOperationBackendVoidOperationFn initialize_send_transport;
    MediaSfuUnityOperationBackendJsonOperationFn create_send_transport_connect_parameters;
    MediaSfuUnityOperationBackendVoidOperationFn complete_send_transport_connect;
    MediaSfuUnityOperationBackendJsonOperationFn create_produce_request;
    MediaSfuUnityOperationBackendVoidOperationFn bind_producer;
    MediaSfuUnityOperationBackendVoidOperationFn pause_producer;
    MediaSfuUnityOperationBackendVoidOperationFn resume_producer;
    MediaSfuUnityOperationBackendVoidOperationFn close_producer;
    MediaSfuUnityOperationBackendVoidOperationFn initialize_receive_transport;
    MediaSfuUnityOperationBackendJsonOperationFn create_receive_transport_connect_parameters;
    MediaSfuUnityOperationBackendVoidOperationFn complete_receive_transport_connect;
    MediaSfuUnityOperationBackendVoidOperationFn bind_consumer;
    MediaSfuUnityOperationBackendVoidOperationFn close_consumer;
    MediaSfuUnityOperationBackendDescribeFn describe;
} MediaSfuUnityOperationBackend;

MEDIASFU_UNITY_BRIDGE_API void MediaSfuUnityInstallOperationBackend(
    const MediaSfuUnityOperationBackend* backend,
    void* installContext);

#ifdef __cplusplus
}
#endif

#endif