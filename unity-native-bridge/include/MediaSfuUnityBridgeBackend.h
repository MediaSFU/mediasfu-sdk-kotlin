#ifndef MEDIASFU_UNITY_BRIDGE_BACKEND_H
#define MEDIASFU_UNITY_BRIDGE_BACKEND_H

#include "MediaSfuUnityBridge.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef void* (*MediaSfuUnityBackendCreateEngineFn)(
    void* installContext,
    const char* createPayloadJson,
    char** errorDetailOut);

typedef void (*MediaSfuUnityBackendDestroyEngineFn)(
    void* installContext,
    void* backendEngine);

typedef char* (*MediaSfuUnityBackendInvokeFn)(
    void* installContext,
    void* backendEngine,
    const char* operationName,
    const char* payloadJson);

typedef const char* (*MediaSfuUnityBackendDescribeFn)(
    void* installContext);

typedef struct MediaSfuUnityWebRtcBackend
{
    MediaSfuUnityBackendCreateEngineFn create_engine;
    MediaSfuUnityBackendDestroyEngineFn destroy_engine;
    MediaSfuUnityBackendInvokeFn invoke;
    MediaSfuUnityBackendDescribeFn describe;
} MediaSfuUnityWebRtcBackend;

MEDIASFU_UNITY_BRIDGE_API void MediaSfuUnityInstallWebRtcBackend(
    const MediaSfuUnityWebRtcBackend* backend,
    void* installContext);

MEDIASFU_UNITY_BRIDGE_API void MediaSfuUnityResetWebRtcBackend(void);

MEDIASFU_UNITY_BRIDGE_API char* MediaSfuUnityDuplicateString(const char* value);

MEDIASFU_UNITY_BRIDGE_API char* MediaSfuUnityCreateFailureResponse(
    const char* errorCode,
    const char* detail);

MEDIASFU_UNITY_BRIDGE_API char* MediaSfuUnityCreateSuccessResponse(
    const char* resultJson);

#ifdef __cplusplus
}
#endif

#endif