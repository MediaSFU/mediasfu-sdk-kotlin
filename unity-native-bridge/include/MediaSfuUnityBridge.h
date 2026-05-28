#ifndef MEDIASFU_UNITY_BRIDGE_H
#define MEDIASFU_UNITY_BRIDGE_H

#if defined(_WIN32)
    #if defined(MEDIASFU_UNITY_BRIDGE_EXPORTS)
        #define MEDIASFU_UNITY_BRIDGE_API __declspec(dllexport)
    #else
        #define MEDIASFU_UNITY_BRIDGE_API __declspec(dllimport)
    #endif
#else
    #define MEDIASFU_UNITY_BRIDGE_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

MEDIASFU_UNITY_BRIDGE_API void* MediaSfuUnityCreateWebRtcEngine(const char* payloadJson);
MEDIASFU_UNITY_BRIDGE_API void MediaSfuUnityDestroyWebRtcEngine(void* engineHandle);
MEDIASFU_UNITY_BRIDGE_API char* MediaSfuUnityInvokeWebRtcEngine(void* engineHandle, const char* operationName, const char* payloadJson);
MEDIASFU_UNITY_BRIDGE_API void MediaSfuUnityFreeString(char* responsePointer);

#ifdef __cplusplus
}
#endif

#endif
