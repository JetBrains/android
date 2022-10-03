load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/adt/idea/android-test-framework:check_tests.bzl", "check_tests")
load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

java_proto_library(
    name = "android-core-proto",
    srcs = glob(["src/com/android/tools/idea/proto/*.proto"]),
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "android-core-proto-test",
    srcs = glob(["testSrc/com/android/tools/idea/proto/*.proto"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core",
    srcs = ["src"],
    iml_files = ["intellij.android.core.iml"],
    javacopts_from_jps = [
        "--add-exports",
        "java.desktop/sun.swing=ALL-UNNAMED",
        "--add-exports",
        "java.desktop/sun.awt.image=ALL-UNNAMED",
        "--add-exports",
        "jdk.attach/sun.tools.attach=ALL-UNNAMED",
    ],
    lint_baseline = "lint_baseline.xml",
    lint_timeout = "long",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/adt/idea/build-common:intellij.android.buildCommon"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin",
        "//tools/adt/idea/gradle-dsl-toml:intellij.android.gradle.dsl.toml",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/analytics[module]",
        "//tools/analytics-library/publisher:analytics-publisher[module]",
        "//tools/adt/idea/build-common:intellij.android.buildCommon[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module]",
        "//tools/adt/idea/rt:intellij.android.rt[module]",
        "//tools/base/draw9patch:studio.android.sdktools.draw9patch[module]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/android/lib:spantable",
        "//prebuilts/tools/common/m2:okio",
        "//prebuilts/tools/common/m2:moshi",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/data-binding:studio.baseLibrary[module]",
        "//tools/data-binding:studio.baseLibrarySupport[module]",
        "//tools/data-binding:studio.compilerCommon[module]",
        "//tools/data-binding:studio.compiler[module]",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module]",
        "//tools/adt/idea/.idea/libraries:baksmali",
        "//tools/adt/idea/.idea/libraries:dexlib2",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module]",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/adt/idea/smali:intellij.android.smali[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer[module]",
        "//tools/adt/idea/android-lang:intellij.android.lang[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/android/lib:instantapps-api",
        "//tools/analytics-library/crash:analytics-crash[module]",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//tools/adt/idea/.idea/libraries:emulator-proto",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:HdrHistogram",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[module]",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository[module]",
        "//tools/base/tracer:studio.android.sdktools.tracer[module]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:perfetto-proto",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module]",
        "//tools/adt/idea/lint:intellij.lint[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/adt/idea/dagger:intellij.android.dagger[module]",
        "//tools/adt/idea/.idea/libraries:utp-core-proto-jarjar",
        "//tools/adt/idea/.idea/libraries:android-test-plugin-host-device-info-proto",
        "//tools/adt/idea/android:libandroid-core-proto",
        "//tools/base/zipflinger:studio.android.sdktools.zipflinger[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module]",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[module]",
        "//tools/adt/idea/intellij.android.compose-common[module]",
        "//tools/adt/idea/server-flags:intellij.android.server-flags[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-webp",
        "//tools/adt/idea/.idea/libraries:kotlinx-coroutines-guava",
        "//tools/adt/idea/android:libstudio.android-test-plugin-host-retention-proto",
        "//tools/adt/idea/utp[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/base/manifest-parser:studio.android.sdktools.manifest-parser[module]",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback[module]",
        "//tools/adt/idea/threading-checker:intellij.android.threading-checker[module]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.tests",
    iml_files = ["intellij.android.core.tests.iml"],
    split_test_targets = {
        "modularize": {
            "test_filter": "com.android.tools.idea.refactoring.modularize",
        },
        "gradle": {
            "shard_count": 6,
            "data": ["//prebuilts/studio/sdk:ndk"],
            "test_filter": "com.android.tools.idea.gradle",
        },
        "gradle.project.sync": {
            "shard_count": 4,
            "data": [
                "//prebuilts/studio/sdk:ndk",
                "//tools/data-binding:data_binding_runtime.zip",
            ],
            # Mismatches from ProjectDumper (b/150709248), and sync errors/timeout (b/152778663)
            "tags": ["no_windows"],
            "test_filter": "com.android.tools.idea.gradle.project.sync",
        },
        "navigator": {
            "shard_count": 2,
            "data": ["//prebuilts/studio/sdk:ndk"],
            "test_filter": "com.android.tools.idea.navigator",
        },
        "imports": {
            "shard_count": 2,
            "test_filter": "com.android.tools.idea.imports",
        },
        "run": {
            "shard_count": 5,
            "test_filter": "com.android.tools.idea.run",
        },
        "testartifacts": {
            "shard_count": 3,
            "test_filter": "com.android.tools.idea.testartifacts",
        },
        "AndroidKotlinCompletionContributorTest": {
            "test_filter": "com.android.tools.idea.completion.AndroidKotlinCompletionContributorTest",
        },
        "editors.manifest": {
            "test_filter": "com.android.tools.idea.editors.manifest",
        },
        "editors.literals": {
            "test_filter": "com.android.tools.idea.editors.literals",
        },
        "instantapp": {
            "test_filter": "com.android.tools.idea.instantapp",
        },
        "rendering": {
            "test_filter": "com.android.tools.idea.rendering",
            "shard_count": 2,
        },
        "other": {
            "shard_count": 7,
        },
    },
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:layoutlib_extensions",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:cmake",
        "//prebuilts/studio/sdk:docs",
        "//prebuilts/studio/sdk:licenses",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:platforms/latest-preview",
        "//prebuilts/studio/sdk:sources",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/lib:apks",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native:libjni_object_tagger.prebuilt",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/compose-ide-plugin/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",  # Please do not add old versions of AGP here. Use the old-agp-tests module instead.
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
        "//tools/base/resource-repository/test/resources",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
    ],
    test_friends = ["//tools/adt/idea/android:intellij.android.core"],
    test_jvm_flags = ["-Djdk.attach.allowAttachSelf=true"],
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
        "block-network",
        "no_test_mac",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/databinding:intellij.android.databinding",
        "//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/.idea/libraries:easymock[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/observable:intellij.android.observable[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module, test]",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository[module, test]",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module, test]",
        "//tools/adt/idea/.idea/libraries:dexlib2[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module, test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/adt/idea/.idea/libraries:guava-testlib[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api[module, test]",
        "//tools/adt/idea/.idea/libraries:equalsverifier[test]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module, test]",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer[module, test]",
        "//tools/adt/idea/.idea/libraries:sqlite[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-lang:intellij.android.lang[module, test]",
        "//tools/adt/idea/android/lib:instantapps-api",
        "//tools/analytics-library/crash:analytics-crash[module, test]",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//tools/adt/idea/.idea/libraries:emulator-proto",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.output.parser[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/app-inspection/inspectors/database:app-inspection.inspectors.database[module]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module, test]",
        "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl[module, test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd[module]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
        "//tools/adt/idea/.idea/libraries:utp-core-proto-jarjar",
        "//tools/adt/idea/.idea/libraries:android-test-plugin-host-device-info-proto",
        "//tools/adt/idea/android:libandroid-core-proto",
        "//tools/adt/idea/android:libandroid-core-proto-test[test]",
        "//tools/adt/idea/server-flags:intellij.android.server-flags[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java-ide-customization[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java-i18n[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images",
        "//tools/adt/idea/android:libstudio.android-test-plugin-host-retention-proto",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/project-system-gradle-repository-search:intellij.android.projectSystem.gradle.repositorySearch[module, test]",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
        "//tools/adt/idea/utp[module, test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//tools/adt/idea/intellij.android.compose-common[module, test]",
        "//tools/base/manifest-parser:studio.android.sdktools.manifest-parser[module]",
        "//tools/base/common:studio.android.sdktools.common.testfixtures[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java[test]",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[module, test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[module, test]",
        "//tools/adt/idea/android-navigator:intellij.android.navigator[module, test]",
        "//tools/adt/idea/android-navigator:intellij.android.navigator.testutils[module, test]",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback[module, test]",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin[module, test]",
    ],
)

filegroup(
    name = "profiler-artifacts",
    srcs = [
        "//tools/base/profiler:perfetto-protos",
        "//tools/base/profiler/app:perfa.jar",
        "//tools/base/profiler/app:perfa_okhttp.dex",
        "//tools/base/profiler/native/trace_processor_daemon:traceprocessor_protos",
        "//tools/base/profiler/transform:profilers-transform.jar",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "deploy-artifacts",
    srcs = select({
        "//tools/base/bazel:windows": [
            "//tools/base/deploy/installer:android-installer",
            "//tools/base/deploy/installer/tests:fake_device_proto",
            "//tools/base/deploy/proto:deploy_java_proto",
        ],
        "//conditions:default": [
            "//tools/base/deploy/installer:test-installer",
            "//tools/base/deploy/installer:android-installer",
            "//tools/base/deploy/installer/tests:artifacts",
            "//tools/base/deploy/proto:deploy_java_proto",
        ],
    }) + [
        "//tools/base/deploy/deployer:java_version",
        "//tools/base/deploy/sites:java_sites",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "transport-artifacts",
    srcs = [
        "//tools/base/transport:android",
        "//tools/base/transport/native/agent:android",
        "//tools/base/transport/proto:transport_java_proto",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "layout-inspector-artifacts",
    srcs = [
        "//prebuilts/tools/common/app-inspection/androidx/compose/ui:layout_inspector_compose_java_proto",
        "//tools/adt/idea/layout-inspector/src/proto:layout_inspector_snapshot_java_proto",
        "//tools/base/dynamic-layout-inspector/agent/appinspection:agent",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
        "//tools/base/dynamic-layout-inspector/skia/proto:layout_inspector_skia_java_proto",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "app-inspection-artifacts",
    srcs = [
        "//prebuilts/tools/common/app-inspection/androidx/sqlite:sqlite_inspector_proto",
        "//prebuilts/tools/common/app-inspection/androidx/work:workmanager_inspector_proto",
        "//tools/base/app-inspection/inspectors/backgroundtask:agent",
        "//tools/base/app-inspection/inspectors/backgroundtask:backgroundtask_inspector_java_proto",
        "//tools/base/app-inspection/inspectors/network:agent",
        "//tools/base/app-inspection/inspectors/network:network_inspector_java_proto",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "directaccess-artifacts",
    srcs = [
        "//tools/adt/idea/.idea/libraries:adb_proxy_proto",
        "//tools/adt/idea/.idea/libraries:directaccess_client_proto",
        "//tools/base/adb-proxy/reverse-daemon:daemon",
    ],
)

# Various artifacts that the ANT and within-idea studio build depend on.
filegroup(
    name = "artifacts",
    srcs = [
        ":android-core-proto",
        ":android-core-proto-test",
        ":app-inspection-artifacts",
        ":deploy-artifacts",
        ":directaccess-artifacts",
        ":layout-inspector-artifacts",
        ":profiler-artifacts",
        ":transport-artifacts",
        "//tools/adt/idea/server-flags:server-flag-test-proto",
        "//tools/adt/idea/emulator/screen-sharing-agent:bundle",
        "//tools/adt/idea/swingp:swing-instrumenter_deploy.jarjar.jar",
        "//tools/analytics-library/protos/src/main/proto:studio.proto",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/build-system:gradle-api_neverlink",  # Provided when run in Gradle.
        "//tools/base/ddmlib:am-instrumentation-data-proto",
        "//tools/base/ddmlib:app-processes-proto",
        "//tools/base/emulator/proto:emulator_java_proto",
        "//tools/base/game-tools/protos:game-tools-protos",
        "//tools/base/lint:lint-checks-proto",
        "//tools/base/sdk-common/aia-manifest-jar-generator:proto",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
        "//tools/base/threading-agent:threading_agent.jar",
        "//tools/base/tracer:trace_agent",
        "//tools/adt/idea/as-driver:as_driver_deploy.jar",
        "//tools/base/bazel:utp-core-proto-jarjar",
        "//tools/base/utp/android-test-plugin-host-device-info-proto:libstudio.android-test-plugin-host-device-info-proto",
        "//tools/base/utp/android-test-plugin-host-retention-proto:libstudio.android-test-plugin-host-retention-proto",
        "//tools/base/utp/android-test-plugin-result-listener-gradle-proto:libstudio.android-test-plugin-result-listener-gradle-proto",
        "//tools/data-binding:compilerCommon.antlr_runtime.shaded",
        "//tools/data-binding:compilerCommon.antlr.shaded",
        "//tools/vendor/google/firebase/src/proto:firebase_java_proto",
        "//tools/vendor/google/url-assistant/src/proto:urlassistant_proto",
        "//tools/adt/idea/build-attribution/proto:build-analysis-results-proto",
    ] + select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            "//tools/adt/idea/bleak/src/com/android/tools/idea/bleak/agents:jnibleakhelper",
        ],
    }),
    visibility = ["//tools/base/bazel:__pkg__"],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "//tools/adt/idea/kotlin-integration:kotlin-android-extensions-runtime-for-ide-plugin",
        "//tools/adt/idea/kotlin-integration:kotlin-gradle-plugin-for-ide-plugin",
        "//tools/adt/idea/kotlin-integration:kotlin-reflect-for-ide-plugin",
        "//tools/adt/idea/kotlin-integration:kotlin-stdlib-jdk8-for-ide-plugin",
        "@maven//:android.arch.navigation.navigation-fragment_1.0.0",
        "@maven//:android.arch.persistence.room.runtime_1.0.0",
        "@maven//:androidx.annotation.annotation_1.2.0-rc01",
        "@maven//:androidx.appcompat.appcompat_1.0.2",
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.benchmark.benchmark-gradle-plugin_1.1.0-beta04",
        "@maven//:androidx.compose.ui.ui-tooling_1.3.0",
        "@maven//:androidx.constraintlayout.constraintlayout_1.1.3",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.core.core-ktx_1.0.1",
        "@maven//:androidx.core.core-ktx_1.2.0",
        "@maven//:androidx.core.core-ktx_1.6.0",
        "@maven//:androidx.core.core_1.5.0-beta01",
        "@maven//:androidx.core.core_1.5.0-rc02",
        "@maven//:androidx.legacy.legacy-support-v4_1.0.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.3.1",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.4.0",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.3.5",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.3.5",
        "@maven//:androidx.palette.palette-ktx_1.0.0",
        "@maven//:androidx.recyclerview.recyclerview_1.2.1",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:androidx.test.espresso.espresso-core_3.1.0",
        "@maven//:androidx.test.espresso.espresso-core_3.2.0",
        "@maven//:androidx.test.ext.junit_1.1.2",
        "@maven//:androidx.test.ext.junit_1.1.3-alpha02",
        "@maven//:com.android.support.appcompat-v7_25.4.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.constraint.constraint-layout_1.1.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.1.3",
        "@maven//:com.android.support.constraint.constraint-layout_2.0.4",
        "@maven//:com.android.support.design_28.0.0",
        "@maven//:com.android.support.multidex-instrumentation_1.0.2",
        "@maven//:com.android.support.support-v13_28.0.0",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.android.tools.desugar_jdk_libs_1.1.5",
        "@maven//:com.google.android.gms.play-services-maps_17.0.1",
        "@maven//:com.google.android.gms.play-services-maps_18.0.2",
        "@maven//:com.google.android.material.material_1.4.0",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.code.gson.gson_2.2.4",
        "@maven//:com.google.dagger.dagger-compiler_2.6",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.firebase.firebase-common_12.0.1",
        "@maven//:com.google.guava.guava_28.2-jre",
        "@maven//:com.google.truth.truth_0.44",
        "@maven//:com.linkedin.testbutler.test-butler-app_1.3.1",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:commons-logging.commons-logging_1.1.1",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.jacoco.org.jacoco.ant_0.8.8",
        "@maven//:org.jdeferred.jdeferred-android-aar_1.2.3",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.5.21",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.5.21",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.31",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.5.21",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.4.31",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.5.21",
        "@maven//:org.mockito.mockito-core_4.8.0",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
    # Do not change: this target is explicitly marked private to avoid bloat.
    visibility = ["//visibility:private"],
)

# Validates no new gradle project tests are added to intellij.android.core.tests
check_tests(
    agp_test_module = ":intellij.android.core.agp-integration-tests",
    disallow_gradle_project_tests = True,
    gradle_project_tests_allowlist = ":intellij.android.core.tests.gradle-tests-allowlist.txt",
    iml_module = ":intellij.android.core.tests",
)

studio_data(
    name = "asset-studio-bundle",
    files = glob(["resources/images/asset_studio/**"]),
    mappings = {
        "tools/adt/idea/android/resources/": "",
    },
    visibility = ["//visibility:public"],
)

# built for updating "resources/gmavenIndex/classes-offline.json".
kotlin_library(
    name = "update_offline_gmaven_index",
    srcs = [
        "src/com/android/tools/idea/imports/GMavenIndexConstants.kt",
        "src/com/android/tools/idea/imports/GzipUtils.kt",
        "src/com/android/tools/idea/imports/UpdateOfflineGMavenIndex.kt",
    ],
    deps = [
        "@maven//:com.google.guava.guava",
        "@maven//:org.apache.commons.commons-compress",
    ],
)

java_binary(
    name = "update_offline_gmaven_index_main",
    main_class = "com.android.tools.idea.imports.UpdateOfflineGMavenIndex",
    runtime_deps = [
        ":update_offline_gmaven_index",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "libandroid-core-proto",
    jars = ["//tools/adt/idea/android:libandroid-core-proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libandroid-core-proto-test",
    jars = ["//tools/adt/idea/android:libandroid-core-proto-test.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libstudio.android-test-plugin-host-retention-proto",
    jars = ["//tools/base/utp/android-test-plugin-host-retention-proto:libstudio.android-test-plugin-host-retention-proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.agp-integration-tests",
    iml_files = ["intellij.android.core.agp-integration-tests.iml"],
    split_test_targets = {
        "modularize": {
            "test_filter": "com.android.tools.idea.refactoring.modularize",
        },
        "UnusedResourcesGradleTest": {
            # The testKotlin test case takes 3 minutes (linux) or 5 minutes (windows).
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "org.jetbrains.android.refactoring.UnusedResourcesGradleTest",
        },
        "MigrateToAndroidxGradleKtsTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "org.jetbrains.android.refactoring.MigrateToAndroidxGradleKtsTest",
        },
        "IllegalIdentifierInspectionTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "com.android.tools.idea.inspections.IllegalIdentifierInspectionTest",
        },
        "other": {
            "shard_count": 3,
            "tags": ["no_windows"],
        },
    },
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        ":test_deps",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData:javadoc",
        "//tools/adt/idea/android/testData:projects",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",  # Please do not add old versions of AGP here. Use the old-agp-tests module instead.
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
    ],
    test_srcs = ["agpIntegrationTestSrc"],
    test_tags = [],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle[test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/adt/idea/observable:intellij.android.observable[module, test]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin[test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[module, test]",
    ],
)
