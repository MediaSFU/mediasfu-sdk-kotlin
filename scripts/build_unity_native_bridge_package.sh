#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
bridge_dir="$repo_dir/unity-native-bridge"
ios_bridge_dir="$repo_dir/ios-native-bridge"
ios_mediasoup_client_dir="$repo_dir/../mediasfu-mediasoup-client-ios"
libmediasoupclient_include_dir="$repo_dir/../mediasfu-mediasoup-client-android/mediasoup-client/deps/libmediasoupclient/include"
libsdptransform_include_dir="$repo_dir/../mediasfu-mediasoup-client-android/mediasoup-client/deps/libmediasoupclient/deps/libsdptransform/include"
webrtc_source_root="$repo_dir/../.native-src/webrtc-ios-full/src"
webrtc_abseil_root="$webrtc_source_root/third_party/abseil-cpp"
unity_client_package_dir="${MEDIASFU_UNITY_CLIENT_PACKAGE_DIR:-$repo_dir/../mediasfu-mediasoup-client-unity}"
unity_plugins_dir="$unity_client_package_dir/Runtime/Plugins"
local_properties="$repo_dir/local.properties"

build_macos=true
build_android=true
build_ios=true
android_abis=("arm64-v8a" "x86_64")

usage() {
    cat <<'EOF'
Usage: build_unity_native_bridge_package.sh [options]

Builds the standalone Unity native bridge and copies the resulting binaries into
the mediasfu-mediasoup-client-unity package plugin folders.

Options:
  --macos-only              Build and package only the macOS editor plugin.
  --android-only            Build and package only Android plugins.
    --ios-only                Build and package only the iOS Unity plugin.
  --android-abis a,b,c      Override the Android ABI list. Default: arm64-v8a,x86_64
  --help                    Show this help text.

Environment:
    MEDIASFU_UNITY_CLIENT_PACKAGE_DIR  Override the Unity client package root.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --macos-only)
            build_android=false
            build_ios=false
            ;;
        --android-only)
            build_macos=false
            build_ios=false
            ;;
        --ios-only)
            build_macos=false
            build_android=false
            ;;
        --android-abis)
            shift
            if [[ $# -eq 0 || -z "$1" ]]; then
                echo "error: --android-abis requires a comma-separated ABI list" >&2
                exit 1
            fi
            IFS=',' read -r -a android_abis <<< "$1"
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo "error: unknown option '$1'" >&2
            usage >&2
            exit 1
            ;;
    esac
    shift
done

copy_artifact() {
    local source_path="$1"
    local destination_path="$2"

    mkdir -p "$(dirname "$destination_path")"
    cp "$source_path" "$destination_path"
    echo "packaged $(basename "$source_path") -> $destination_path"
}

copy_tree() {
    local source_path="$1"
    local destination_path="$2"

    mkdir -p "$(dirname "$destination_path")"
    rm -rf "$destination_path"
    cp -R "$source_path" "$destination_path"
    echo "packaged $(basename "$source_path") -> $destination_path"
}

resolve_sdk_dir() {
    if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
        printf '%s\n' "$ANDROID_SDK_ROOT"
        return 0
    fi

    if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
        printf '%s\n' "$ANDROID_HOME"
        return 0
    fi

    if [[ -f "$local_properties" ]]; then
        local sdk_dir
        sdk_dir="$(sed -n 's/^sdk.dir=//p' "$local_properties" | tail -n 1)"
        if [[ -n "$sdk_dir" && -d "$sdk_dir" ]]; then
            printf '%s\n' "$sdk_dir"
            return 0
        fi
    fi

    return 1
}

resolve_ndk_dir() {
    if [[ -n "${ANDROID_NDK_HOME:-}" && -d "${ANDROID_NDK_HOME}" ]]; then
        printf '%s\n' "$ANDROID_NDK_HOME"
        return 0
    fi

    if [[ -n "${ANDROID_NDK_ROOT:-}" && -d "${ANDROID_NDK_ROOT}" ]]; then
        printf '%s\n' "$ANDROID_NDK_ROOT"
        return 0
    fi

    local sdk_dir
    sdk_dir="$(resolve_sdk_dir)" || return 1

    if [[ -d "$sdk_dir/ndk" ]]; then
        local latest_ndk
        latest_ndk="$(find "$sdk_dir/ndk" -mindepth 1 -maxdepth 1 -type d | LC_ALL=C sort | tail -n 1)"
        if [[ -n "$latest_ndk" ]]; then
            printf '%s\n' "$latest_ndk"
            return 0
        fi
    fi

    if [[ -d "$sdk_dir/ndk-bundle" ]]; then
        printf '%s\n' "$sdk_dir/ndk-bundle"
        return 0
    fi

    return 1
}

build_macos_plugin() {
    if [[ "$(uname -s)" != "Darwin" ]]; then
        echo "skipping macOS plugin packaging on non-Darwin host $(uname -s)"
        return 0
    fi

    local build_dir="$bridge_dir/build"
    local archive_path
    local libmediasoupclient_archive="$ios_mediasoup_client_dir/Frameworks/libmediasoupclient.xcframework/macos-arm64/libmediasoupclient.a"
    local libsdptransform_archive="$ios_mediasoup_client_dir/.native-build/libmediasoupclient/macos-arm64/libsdptransform/libsdptransform.a"
    local webrtc_build_dir="$webrtc_source_root/out_mediasfu_macos_arm64"
    local webrtc_link_ninja="$webrtc_build_dir/obj/sdk/mac_framework_objc_shared_library.ninja"
    local webrtc_link_line
    local webrtc_link_blob
    local webrtc_frameworks_line
    local ninja_bin
    local swift_build_dir
    local swift_bin_dir
    local output_path
    local destination_path="$unity_plugins_dir/macOS/libMediaSFUNativeBridge.dylib"
    local stale_webrtc_destination="$unity_plugins_dir/macOS/WebRTC.framework"
    local stale_webrtc_meta="$unity_plugins_dir/macOS/WebRTC.framework.meta"
    local -a webrtc_link_inputs=()
    local -a webrtc_link_tokens=()
    local -a webrtc_framework_flags=()
    local -a extra_webrtc_archives=(
        "$webrtc_build_dir/obj/api/libcreate_peerconnection_factory.a"
        "$webrtc_build_dir/obj/api/video_codecs/libbuiltin_video_encoder_factory.a"
        "$webrtc_build_dir/obj/api/video_codecs/libbuiltin_video_decoder_factory.a"
        "$webrtc_build_dir/obj/api/video_codecs/librtc_software_fallback_wrappers.a"
        "$webrtc_build_dir/obj/media/librtc_internal_video_codecs.a"
        "$webrtc_build_dir/obj/media/librtc_simulcast_encoder_adapter.a"
        "$webrtc_build_dir/obj/test/libfake_video_codecs.a"
    )

    cmake -S "$bridge_dir" -B "$build_dir"
    cmake --build "$build_dir"
    ctest --test-dir "$build_dir" --output-on-failure

    if [[ ! -d "$ios_bridge_dir" ]]; then
        echo "error: expected iOS bridge package at $ios_bridge_dir" >&2
        exit 1
    fi

    # Link the real Swift bridge archive into the Unity-loadable macOS dylib.
    swift_build_dir="$(mktemp -d "${TMPDIR:-/tmp}/mediasfu-unity-macos-plugin.XXXXXX")"

    pushd "$ios_bridge_dir" >/dev/null
    swift build -c release --scratch-path "$swift_build_dir" --product MediaSFUIosBridge
    swift_bin_dir="$(swift build -c release --scratch-path "$swift_build_dir" --show-bin-path)"
    popd >/dev/null

    archive_path="$swift_bin_dir/libMediaSFUIosBridge.a"
    output_path="$swift_build_dir/libMediaSFUNativeBridge.dylib"

    if [[ ! -f "$archive_path" ]]; then
        echo "error: expected macOS bridge archive at $archive_path" >&2
        exit 1
    fi

    if [[ ! -f "$libmediasoupclient_archive" ]]; then
        echo "error: expected macOS libmediasoupclient archive at $libmediasoupclient_archive" >&2
        exit 1
    fi

    if [[ ! -f "$libsdptransform_archive" ]]; then
        echo "error: expected macOS libsdptransform archive at $libsdptransform_archive" >&2
        exit 1
    fi

    if [[ ! -f "$webrtc_link_ninja" ]]; then
        echo "error: expected macOS WebRTC link manifest at $webrtc_link_ninja" >&2
        exit 1
    fi

    ninja_bin="$webrtc_source_root/third_party/ninja/ninja"
    if [[ ! -x "$ninja_bin" ]]; then
        ninja_bin="$(command -v ninja || true)"
    fi
    if [[ -z "$ninja_bin" ]]; then
        echo "error: ninja is required to materialize additional macOS WebRTC archives" >&2
        exit 1
    fi

    webrtc_link_line="$(grep '^build obj/sdk/mac_framework_objc_shared_library/WebRTC obj/sdk/mac_framework_objc_shared_library/WebRTC.TOC: solink ' "$webrtc_link_ninja" || true)"
    if [[ -z "$webrtc_link_line" ]]; then
        echo "error: failed to locate the macOS WebRTC solink rule in $webrtc_link_ninja" >&2
        exit 1
    fi

    webrtc_link_blob="${webrtc_link_line#*: solink }"
    webrtc_link_blob="${webrtc_link_blob%% || *}"
    read -r -a webrtc_link_tokens <<< "$webrtc_link_blob"
    for token in "${webrtc_link_tokens[@]}"; do
        if [[ "$token" == *.o || "$token" == *.a ]]; then
            if [[ "$token" = /* ]]; then
                webrtc_link_inputs+=( "$token" )
            else
                webrtc_link_inputs+=( "$webrtc_build_dir/$token" )
            fi
        fi
    done

    if [[ "${#webrtc_link_inputs[@]}" -eq 0 ]]; then
        echo "error: failed to extract macOS WebRTC native link inputs from $webrtc_link_ninja" >&2
        exit 1
    fi

    if [[ ! -f "${extra_webrtc_archives[0]}" || ! -f "${extra_webrtc_archives[1]}" || ! -f "${extra_webrtc_archives[2]}" || ! -f "${extra_webrtc_archives[3]}" || ! -f "${extra_webrtc_archives[4]}" || ! -f "${extra_webrtc_archives[5]}" || ! -f "${extra_webrtc_archives[6]}" ]]; then
        "$ninja_bin" -C "$webrtc_build_dir" \
            obj/api/libcreate_peerconnection_factory.a \
            obj/api/video_codecs/libbuiltin_video_encoder_factory.a \
            obj/api/video_codecs/libbuiltin_video_decoder_factory.a \
            obj/api/video_codecs/librtc_software_fallback_wrappers.a \
            obj/media/librtc_internal_video_codecs.a \
            obj/media/librtc_simulcast_encoder_adapter.a \
            obj/test/libfake_video_codecs.a
    fi

    for extra_archive in "${extra_webrtc_archives[@]}"; do
        if [[ ! -f "$extra_archive" ]]; then
            echo "error: expected macOS WebRTC archive at $extra_archive" >&2
            exit 1
        fi
        webrtc_link_inputs+=( "$extra_archive" )
    done

    webrtc_frameworks_line="$(sed -n 's/^  frameworks = //p' "$webrtc_link_ninja" | head -n 1)"
    if [[ -n "$webrtc_frameworks_line" ]]; then
        read -r -a webrtc_framework_flags <<< "$webrtc_frameworks_line"
    fi

    xcrun clang++ -dynamiclib \
        -o "$output_path" \
        -Wl,-force_load,"$archive_path" \
        -Wl,-force_load,"$libmediasoupclient_archive" \
        -Wl,-force_load,"$libsdptransform_archive" \
        "${webrtc_link_inputs[@]}" \
        -Wl,-install_name,@rpath/libMediaSFUNativeBridge.dylib \
        -Wl,-rpath,@loader_path \
        -Wl,-ObjC \
        -Wl,-no_warn_duplicate_libraries \
        "${webrtc_framework_flags[@]}" \
        -lc++

    local exported_symbols
    exported_symbols="$(nm -gU "$output_path")"

    for required_symbol in \
        _MediaSfuUnityCreateWebRtcEngine \
        _MediaSfuUnityDestroyWebRtcEngine \
        _MediaSfuUnityInvokeWebRtcEngine \
        _MediaSfuUnityFreeString
    do
        if ! grep -Fq "$required_symbol" <<< "$exported_symbols"; then
            echo "error: expected macOS bridge artifact to export $required_symbol" >&2
            exit 1
        fi
    done

    copy_artifact "$output_path" "$destination_path"
    rm -rf "$stale_webrtc_destination" "$stale_webrtc_meta"

    rm -rf "$swift_build_dir"
}

build_android_plugin() {
    local ndk_dir="$1"
    local abi="$2"
    local build_dir="$bridge_dir/build-android/$abi"
    local output_path="$build_dir/libMediaSFUNativeBridge.so"
    local destination_path="$unity_plugins_dir/Android/$abi/libMediaSFUNativeBridge.so"

    cmake -S "$bridge_dir" -B "$build_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$ndk_dir/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM=android-24 \
        -DCMAKE_BUILD_TYPE=Release
    cmake --build "$build_dir"

    if [[ ! -f "$output_path" ]]; then
        echo "error: expected Android bridge artifact at $output_path" >&2
        exit 1
    fi

    copy_artifact "$output_path" "$destination_path"
}

build_ios_plugin() {
    if [[ "$(uname -s)" != "Darwin" ]]; then
        echo "skipping iOS plugin packaging on non-Darwin host $(uname -s)"
        return 0
    fi

    if ! command -v xcodebuild >/dev/null 2>&1; then
        echo "skipping iOS plugin packaging because xcodebuild is unavailable"
        return 0
    fi

    local unity_headers_dir="$bridge_dir/include"
    local build_dir="$ios_bridge_dir/.build/unity-ios-plugin"
    local simulator_derived_data="$ios_bridge_dir/.build/derived-sim-release"
    local device_derived_data="$ios_bridge_dir/.build/derived-device-release"
    local simulator_products="$simulator_derived_data/Build/Products/Release-iphonesimulator"
    local device_products="$device_derived_data/Build/Products/Release-iphoneos"
    local simulator_libsdptransform="$ios_mediasoup_client_dir/.native-build/libmediasoupclient/simulator-arm64-x86_64/libsdptransform/libsdptransform.a"
    local device_libsdptransform="$ios_mediasoup_client_dir/.native-build/libmediasoupclient/device-arm64/libsdptransform/libsdptransform.a"
    local simulator_output="$build_dir/libMediaSFUNativeBridge-sim.a"
    local device_output="$build_dir/libMediaSFUNativeBridge-device.a"
    local packaged_dir="$unity_plugins_dir/iOS"
    local xcframework_output="$packaged_dir/MediaSFUNativeBridge.xcframework"
    local webrtc_output="$packaged_dir/WebRTC.xcframework"
    local common_cflags='-DWEBRTC_MAC -DWEBRTC_POSIX'
    local common_cxxflags="-std=gnu++20 -DWEBRTC_MAC -DWEBRTC_POSIX -I$libmediasoupclient_include_dir -I$libsdptransform_include_dir -I$webrtc_source_root"

    if [[ -d "$webrtc_abseil_root" ]]; then
        common_cxxflags="$common_cxxflags -I$webrtc_abseil_root"
    fi

    run_xcodebuild_or_accept_object_harvest() {
        local products_dir="$1"
        shift

        if "$@"; then
            return 0
        fi

        local required_path
        for required_path in \
            "$products_dir/MediaSFUIosBridge.o" \
            "$products_dir/MediaSFUMediasoupClient.o" \
            "$products_dir/CLibmediasoupclient.o" \
            "$products_dir/libmediasoupclient.a"
        do
            if [[ ! -e "$required_path" ]]; then
                echo "error: xcodebuild failed before producing required object-harvest artifact at $required_path" >&2
                return 1
            fi
        done

        echo "warning: xcodebuild exited non-zero after producing required iOS bridge objects; continuing with static packaging" >&2
    }

    if [[ ! -d "$ios_bridge_dir" ]]; then
        echo "error: expected iOS bridge package at $ios_bridge_dir" >&2
        exit 1
    fi

    if [[ ! -d "$ios_mediasoup_client_dir/Frameworks/WebRTC.xcframework" ]]; then
        echo "error: expected WebRTC.xcframework at $ios_mediasoup_client_dir/Frameworks/WebRTC.xcframework" >&2
        exit 1
    fi

    if [[ ! -f "$simulator_libsdptransform" ]]; then
        echo "error: expected simulator libsdptransform archive at $simulator_libsdptransform" >&2
        exit 1
    fi

    if [[ ! -f "$device_libsdptransform" ]]; then
        echo "error: expected device libsdptransform archive at $device_libsdptransform" >&2
        exit 1
    fi

    if [[ ! -d "$webrtc_source_root" ]]; then
        echo "error: expected staged WebRTC sources at $webrtc_source_root" >&2
        exit 1
    fi

    mkdir -p "$build_dir"

    # SwiftPM does not emit a linkable framework for this mixed Swift/ObjC++ package,
    # so build release objects for each iOS slice and assemble the static bridge archive.
    pushd "$ios_bridge_dir" >/dev/null

    run_xcodebuild_or_accept_object_harvest "$simulator_products" \
        env WEBRTC_SRC_ROOT="$webrtc_source_root" WEBRTC_ABSEIL_ROOT="$webrtc_abseil_root" xcodebuild build -quiet \
        -configuration Release \
        -scheme MediaSFUIosBridge \
        -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath "$simulator_derived_data" \
        SKIP_INSTALL=NO \
        BUILD_LIBRARY_FOR_DISTRIBUTION=NO \
        COMPILER_INDEX_STORE_ENABLE=NO \
        CLANG_CXX_LANGUAGE_STANDARD=gnu++20 \
        OTHER_CFLAGS="$common_cflags" \
        OTHER_CPLUSPLUSFLAGS="$common_cxxflags"

    run_xcodebuild_or_accept_object_harvest "$device_products" \
        env WEBRTC_SRC_ROOT="$webrtc_source_root" WEBRTC_ABSEIL_ROOT="$webrtc_abseil_root" xcodebuild build -quiet \
        -configuration Release \
        -scheme MediaSFUIosBridge \
        -destination 'generic/platform=iOS' \
        -derivedDataPath "$device_derived_data" \
        SKIP_INSTALL=NO \
        BUILD_LIBRARY_FOR_DISTRIBUTION=NO \
        COMPILER_INDEX_STORE_ENABLE=NO \
        CLANG_CXX_LANGUAGE_STANDARD=gnu++20 \
        OTHER_CFLAGS="$common_cflags" \
        OTHER_CPLUSPLUSFLAGS="$common_cxxflags"

    popd >/dev/null

    for required_path in \
        "$simulator_products/MediaSFUIosBridge.o" \
        "$simulator_products/MediaSFUMediasoupClient.o" \
        "$simulator_products/CLibmediasoupclient.o" \
        "$simulator_products/libmediasoupclient.a" \
        "$device_products/MediaSFUIosBridge.o" \
        "$device_products/MediaSFUMediasoupClient.o" \
        "$device_products/CLibmediasoupclient.o" \
        "$device_products/libmediasoupclient.a"
    do
        if [[ ! -e "$required_path" ]]; then
            echo "error: expected build artifact at $required_path" >&2
            exit 1
        fi
    done

    libtool -static -o "$simulator_output" \
        "$simulator_products/MediaSFUIosBridge.o" \
        "$simulator_products/MediaSFUMediasoupClient.o" \
        "$simulator_products/CLibmediasoupclient.o" \
        "$simulator_products/libmediasoupclient.a" \
        "$simulator_libsdptransform"

    libtool -static -o "$device_output" \
        "$device_products/MediaSFUIosBridge.o" \
        "$device_products/MediaSFUMediasoupClient.o" \
        "$device_products/CLibmediasoupclient.o" \
        "$device_products/libmediasoupclient.a" \
        "$device_libsdptransform"

    if [[ ! -f "$device_output" ]]; then
        echo "error: expected iPhoneOS bridge artifact at $device_output" >&2
        exit 1
    fi

    if [[ ! -f "$simulator_output" ]]; then
        echo "error: expected iPhoneSimulator bridge artifact at $simulator_output" >&2
        exit 1
    fi

    rm -rf "$xcframework_output"
    mkdir -p "$packaged_dir"
    xcodebuild -create-xcframework \
        -library "$device_output" -headers "$unity_headers_dir" \
        -library "$simulator_output" -headers "$unity_headers_dir" \
        -output "$xcframework_output"

    copy_tree "$ios_mediasoup_client_dir/Frameworks/WebRTC.xcframework" "$webrtc_output"

    echo "packaged MediaSFUNativeBridge.xcframework -> $xcframework_output"
}

if $build_macos; then
    build_macos_plugin
fi

if $build_android; then
    if ndk_dir="$(resolve_ndk_dir 2>/dev/null)"; then
        echo "using Android NDK at $ndk_dir"
        for abi in "${android_abis[@]}"; do
            build_android_plugin "$ndk_dir" "$abi"
        done
    else
        echo "skipping Android plugin packaging because no Android NDK was found"
    fi
fi

if $build_ios; then
    build_ios_plugin
fi

echo "Unity native bridge packaging complete"