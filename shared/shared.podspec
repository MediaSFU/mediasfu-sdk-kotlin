Pod::Spec.new do |spec|
    spec.name                     = 'shared'
    spec.version                  = '1.0.3'
    spec.homepage                 = 'https://github.com/MediaSFU/mediasfu-sdk-kotlin'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'MediaSFU Kotlin Multiplatform SDK'
    spec.vendored_frameworks      = 'build/cocoapods/framework/MediaSFUSDK.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '14.1'
    spec.dependency 'WebRTC'
                
    if !Dir.exist?('build/cocoapods/framework/MediaSFUSDK.framework') || Dir.empty?('build/cocoapods/framework/MediaSFUSDK.framework')
        raise "

        Kotlin framework 'MediaSFUSDK' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:

            ./gradlew :shared:generateDummyFramework

        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
                
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
                
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':shared',
        'PRODUCT_MODULE_NAME' => 'MediaSFUSDK',
    }
                
    spec.script_phases = [
        {
            :name => 'Build shared',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                                KOTLIN_DEVELOPER_DIR="${MEDIA_SFU_KOTLIN_DEVELOPER_DIR:-}"
                                if [ -z "$KOTLIN_DEVELOPER_DIR" ] && [ "${XCODE_VERSION_MAJOR:-0}" -ge 2600 ]; then
                                    LEGACY_XCODE_APP="${MEDIA_SFU_KOTLIN_LEGACY_XCODE_APP:-/Applications/Xcode-15.3.0.app}"
                                    LEGACY_DEVELOPER_DIR="$LEGACY_XCODE_APP/Contents/Developer"
                                    if [ -d "$LEGACY_DEVELOPER_DIR" ]; then
                                        KOTLIN_DEVELOPER_DIR="$LEGACY_DEVELOPER_DIR"
                                        echo "Using legacy DEVELOPER_DIR for Kotlin/Native CocoaPods sync: $KOTLIN_DEVELOPER_DIR"
                                    fi
                                fi
                                if [ -n "$KOTLIN_DEVELOPER_DIR" ]; then
                                    export DEVELOPER_DIR="$KOTLIN_DEVELOPER_DIR"
                                fi
                if [ "$CONFIGURATION" = "Release" ]; then
                  LINK_BUILD_TYPE="Release"
                else
                  LINK_BUILD_TYPE="Debug"
                fi
                case "$PLATFORM_NAME" in
                  *simulator*)
                    case "$ARCHS" in
                      x86_64) LINK_TARGET="IosX64" ;;
                      *)      LINK_TARGET="IosSimulatorArm64" ;;
                    esac ;;
                  *)
                    LINK_TARGET="IosArm64" ;;
                esac
                "$REPO_ROOT/../gradlew" --no-daemon -p "$REPO_ROOT/.." ":shared:linkPod${LINK_BUILD_TYPE}Framework${LINK_TARGET}"
                # Copy the built framework to the vendored_frameworks location
                case "$PLATFORM_NAME" in
                  *simulator*)
                    case "$ARCHS" in
                      x86_64) ARCH_DIR="iosX64" ;;
                      *)      ARCH_DIR="iosSimulatorArm64" ;;
                    esac ;;
                  *)
                    ARCH_DIR="iosArm64" ;;
                esac
                SRC_FRAMEWORK="$REPO_ROOT/build/bin/${ARCH_DIR}/pod${LINK_BUILD_TYPE}Framework/MediaSFUSDK.framework"
                DST_FRAMEWORK="$REPO_ROOT/build/cocoapods/framework/MediaSFUSDK.framework"
                if [ -d "$SRC_FRAMEWORK" ]; then
                    rm -rf "$DST_FRAMEWORK"
                    cp -r "$SRC_FRAMEWORK" "$DST_FRAMEWORK"
                fi
            SCRIPT
        }
    ]
    spec.resources = ['build/compose/cocoapods/compose-resources']
end
