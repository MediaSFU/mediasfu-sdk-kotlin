#!/usr/bin/env ruby
require 'pathname'
require 'xcodeproj'

root = Pathname(__dir__).parent
project_path = root.join('MediaSFUSampleApp.xcodeproj')
project_path.rmtree if project_path.exist?

def ensure_embed_frameworks_phase(target)
  existing_phase = target.copy_files_build_phases.find { |phase| phase.name == 'Embed Frameworks' }
  return existing_phase if existing_phase

  target.new_copy_files_build_phase('Embed Frameworks').tap do |phase|
    phase.dst_subfolder_spec = '10'
  end
end

def add_local_swift_package(project, target, relative_path, product_name, embed: false)
  package_reference = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  package_reference.relative_path = relative_path
  project.root_object.package_references << package_reference

  product_dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  product_dependency.package = package_reference
  product_dependency.product_name = product_name
  target.package_product_dependencies << product_dependency

  build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  build_file.product_ref = product_dependency
  target.frameworks_build_phase.files << build_file

  return unless embed

  embed_build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  embed_build_file.product_ref = product_dependency
  embed_build_file.settings = { 'ATTRIBUTES' => %w[CodeSignOnCopy RemoveHeadersOnCopy] }
  ensure_embed_frameworks_phase(target).files << embed_build_file
end

def ensure_webrtc_sanitize_phase(target)
  existing_phase = target.shell_script_build_phases.find { |phase| phase.name == 'Sanitize Embedded WebRTC' }
  return existing_phase if existing_phase

  target.new_shell_script_build_phase('Sanitize Embedded WebRTC').tap do |phase|
    phase.shell_path = '/bin/bash'
    phase.shell_script = <<~SCRIPT
      set -eu

      web_rtc_framework_path="${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}/WebRTC.framework"
      if [[ ! -d "$web_rtc_framework_path" ]]; then
        exit 0
      fi

      rm -rf "$web_rtc_framework_path/_CodeSignature"
      xattr -cr "$web_rtc_framework_path" >/dev/null 2>&1 || true
      /usr/bin/codesign --remove-signature "$web_rtc_framework_path" >/dev/null 2>&1 || true

      if [[ "${CODE_SIGNING_ALLOWED:-NO}" != "YES" || -z "${EXPANDED_CODE_SIGN_IDENTITY:-}" ]]; then
        exit 0
      fi

      /usr/bin/codesign \
        --force \
        --sign "$EXPANDED_CODE_SIGN_IDENTITY" \
        --timestamp=none \
        --preserve-metadata=identifier,entitlements,flags \
        --generate-entitlement-der \
        "$web_rtc_framework_path"
    SCRIPT
  end
end

  def buildable_reference(target, buildable_name)
    <<~XML
          <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "#{target.uuid}"
            BuildableName = "#{buildable_name}"
            BlueprintName = "#{target.name}"
            ReferencedContainer = "container:MediaSFUSampleApp.xcodeproj">
          </BuildableReference>
    XML
  end

  def write_shared_scheme(project_path, app_target, ui_test_target)
    schemes_dir = project_path.join('xcshareddata', 'xcschemes')
    schemes_dir.mkpath
    scheme_path = schemes_dir.join('MediaSFUSampleApp.xcscheme')

    app_reference = buildable_reference(app_target, 'MediaSFUSampleApp.app')
    ui_test_reference = buildable_reference(ui_test_target, 'MediaSFUSampleAppUITests.xctest')

    scheme_path.write(<<~XML)
     <?xml version="1.0" encoding="UTF-8"?>
     <Scheme
       LastUpgradeVersion = "1530"
       version = "1.7">
       <BuildAction
         parallelizeBuildables = "YES"
         buildImplicitDependencies = "YES"
         buildArchitectures = "Automatic">
         <BuildActionEntries>
           <BuildActionEntry
             buildForTesting = "YES"
             buildForRunning = "YES"
             buildForProfiling = "YES"
             buildForArchiving = "YES"
             buildForAnalyzing = "YES">
     #{app_reference.chomp}
           </BuildActionEntry>
         </BuildActionEntries>
       </BuildAction>
       <TestAction
         buildConfiguration = "Debug"
         selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
         selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
         shouldUseLaunchSchemeArgsEnv = "YES">
         <Testables>
           <TestableReference
             skipped = "NO">
     #{ui_test_reference.chomp}
           </TestableReference>
         </Testables>
       </TestAction>
       <LaunchAction
         buildConfiguration = "Debug"
         selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
         selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
         launchStyle = "0"
         useCustomWorkingDirectory = "NO"
         ignoresPersistentStateOnLaunch = "NO"
         debugDocumentVersioning = "YES"
         debugServiceExtension = "internal"
         allowLocationSimulation = "YES">
         <BuildableProductRunnable
           runnableDebuggingMode = "0">
     #{app_reference.chomp}
         </BuildableProductRunnable>
       </LaunchAction>
       <ProfileAction
         buildConfiguration = "Release"
         shouldUseLaunchSchemeArgsEnv = "YES"
         savedToolIdentifier = ""
         useCustomWorkingDirectory = "NO"
         debugDocumentVersioning = "YES">
         <BuildableProductRunnable
           runnableDebuggingMode = "0">
     #{app_reference.chomp}
         </BuildableProductRunnable>
       </ProfileAction>
       <AnalyzeAction
         buildConfiguration = "Debug">
       </AnalyzeAction>
       <ArchiveAction
         buildConfiguration = "Release"
         revealArchiveInOrganizer = "YES">
       </ArchiveAction>
     </Scheme>
    XML
  end

project = Xcodeproj::Project.new(project_path.to_s)
project.root_object.attributes['LastSwiftUpdateCheck'] = '1530'
project.root_object.attributes['LastUpgradeCheck'] = '1530'
project.root_object.compatibility_version = 'Xcode 15.0'

app_root = root.join('MediaSFUSampleApp')
main_group = project.main_group
app_group = main_group.new_group('MediaSFUSampleApp', 'MediaSFUSampleApp')
app_group.set_source_tree('<group>')

groups = {
  'App' => app_group.new_group('App', 'App'),
  'Bootstrap' => app_group.new_group('Bootstrap', 'Bootstrap'),
  'MediaSFUHost' => app_group.new_group('MediaSFUHost', 'MediaSFUHost'),
  'Permissions' => app_group.new_group('Permissions', 'Permissions'),
  'ScreenShare' => app_group.new_group('ScreenShare', 'ScreenShare'),
  'Resources' => app_group.new_group('Resources', 'Resources'),
}

ui_test_group = main_group.new_group('MediaSFUSampleAppUITests', 'MediaSFUSampleAppUITests')
ui_test_group.set_source_tree('<group>')

target = project.new_target(:application, 'MediaSFUSampleApp', :ios, '15.0')
target.product_reference.name = 'MediaSFUSampleApp.app'

ui_test_target = project.new_target(:ui_test_bundle, 'MediaSFUSampleAppUITests', :ios, '15.0')
ui_test_target.product_reference.name = 'MediaSFUSampleAppUITests.xctest'

target.build_configurations.each do |config|
  settings = config.build_settings
  settings['PRODUCT_NAME'] = '$(TARGET_NAME)'
  settings['PRODUCT_BUNDLE_IDENTIFIER'] = 'com.mediasfu.MediaSFUSampleApp'
  settings['INFOPLIST_FILE'] = 'MediaSFUSampleApp/Resources/Info.plist'
  settings['SWIFT_VERSION'] = '5.0'
  settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.0'
  settings['TARGETED_DEVICE_FAMILY'] = '1,2'
  settings['CODE_SIGN_STYLE'] = 'Automatic'
  settings['DEVELOPMENT_TEAM'] = ''
  settings['GENERATE_INFOPLIST_FILE'] = 'NO'
  settings['ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS'] = 'YES'
  settings['ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES'] = 'YES'
  settings['CLANG_ENABLE_MODULES'] = 'YES'
  settings['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks']
  settings['FRAMEWORK_SEARCH_PATHS'] = ['$(inherited)', '$(PROJECT_DIR)/../shared/build/cocoapods/framework']
  settings['SUPPORTED_PLATFORMS'] = 'iphoneos iphonesimulator'
  settings['SUPPORTS_MACCATALYST'] = 'NO'
  settings['SDKROOT'] = 'iphoneos'
  settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]'] = 'x86_64'
end

ui_test_target.add_dependency(target)
ui_test_target.build_configurations.each do |config|
  settings = config.build_settings
  settings['PRODUCT_NAME'] = '$(TARGET_NAME)'
  settings['PRODUCT_BUNDLE_IDENTIFIER'] = 'com.mediasfu.MediaSFUSampleAppUITests'
  settings['SWIFT_VERSION'] = '5.0'
  settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.0'
  settings['TARGETED_DEVICE_FAMILY'] = '1,2'
  settings['CODE_SIGN_STYLE'] = 'Automatic'
  settings['DEVELOPMENT_TEAM'] = ''
  settings['GENERATE_INFOPLIST_FILE'] = 'YES'
  settings['TEST_TARGET_NAME'] = 'MediaSFUSampleApp'
  settings['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks', '@loader_path/Frameworks']
  settings['FRAMEWORK_SEARCH_PATHS'] = ['$(inherited)', '$(PROJECT_DIR)/../shared/build/cocoapods/framework']
  settings['SUPPORTED_PLATFORMS'] = 'iphoneos iphonesimulator'
  settings['SUPPORTS_MACCATALYST'] = 'NO'
  settings['SDKROOT'] = 'iphoneos'
  settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]'] = 'x86_64'
end

swift_files = app_root.glob('**/*.swift').sort
swift_files.each do |file|
  relative = file.relative_path_from(app_root)
  top_level = relative.each_filename.first
  group = groups.fetch(top_level)
  nested = relative.each_filename.to_a.drop(1).join('/')
  file_ref = group.new_file(nested)
  target.add_file_references([file_ref])
end

plist_ref = groups['Resources'].new_file('Info.plist')
plist_ref.include_in_index = '0'

ui_test_root = root.join('MediaSFUSampleAppUITests')
ui_test_root.glob('**/*.swift').sort.each do |file|
  relative = file.relative_path_from(ui_test_root)
  file_ref = ui_test_group.new_file(relative.to_s)
  ui_test_target.add_file_references([file_ref])
end

if ENV['MEDIA_SFU_ENABLE_IOS_NATIVE_BRIDGE_PACKAGE'] == '1'
  add_local_swift_package(project, target, '../ios-native-bridge', 'MediaSFUIosBridge', embed: true)
end

ensure_webrtc_sanitize_phase(target)

project.save
write_shared_scheme(project_path, target, ui_test_target)
puts "Generated #{project_path}"
