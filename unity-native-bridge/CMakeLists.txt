cmake_minimum_required(VERSION 3.18)

project(MediaSFUNativeBridge LANGUAGES C)

option(MEDIASFU_UNITY_BRIDGE_BUILD_TESTS "Build unity-native-bridge smoke tests" ON)
set(MEDIASFU_UNITY_BRIDGE_LIBRARY_TYPE "SHARED" CACHE STRING "Library type for MediaSFUNativeBridge (STATIC or SHARED)")
set_property(CACHE MEDIASFU_UNITY_BRIDGE_LIBRARY_TYPE PROPERTY STRINGS SHARED STATIC)

add_library(MediaSFUNativeBridge ${MEDIASFU_UNITY_BRIDGE_LIBRARY_TYPE}
    src/MediaSfuUnityOperationBackend.c
    src/MediaSfuUnityBridgeStub.c
)

if(ANDROID)
    target_sources(MediaSFUNativeBridge PRIVATE
        src/MediaSfuUnityAndroidJniBackend.c
    )
endif()

target_include_directories(MediaSFUNativeBridge
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_definitions(MediaSFUNativeBridge
    PRIVATE
        MEDIASFU_UNITY_BRIDGE_EXPORTS
)

set_target_properties(MediaSFUNativeBridge PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
    OUTPUT_NAME MediaSFUNativeBridge
)

if(MEDIASFU_UNITY_BRIDGE_BUILD_TESTS)
    enable_testing()

    add_executable(MediaSFUNativeBridgeOperationBackendSmokeTest
        tests/MediaSfuUnityOperationBackendSmokeTest.c
    )

    target_include_directories(MediaSFUNativeBridgeOperationBackendSmokeTest
        PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/include
    )

    target_link_libraries(MediaSFUNativeBridgeOperationBackendSmokeTest
        PRIVATE
            MediaSFUNativeBridge
    )

    set_target_properties(MediaSFUNativeBridgeOperationBackendSmokeTest PROPERTIES
        BUILD_RPATH $<TARGET_FILE_DIR:MediaSFUNativeBridge>
    )

    add_test(
        NAME MediaSFUNativeBridgeOperationBackendSmokeTest
        COMMAND MediaSFUNativeBridgeOperationBackendSmokeTest
    )
endif()
