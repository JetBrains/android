load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "INTEGRATION_TEST_SYSTEM_IMAGE")
load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "mac",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector",
    srcs = ["src"],
    iml_files = ["intellij.android.layout-inspector.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    test_srcs = ["testingSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common[module]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api[module]",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide[module]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//tools/adt/idea/analytics[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector.tests",
    iml_files = ["intellij.android.layout-inspector.tests.iml"],
    test_class = "com.android.tools.idea.layoutinspector.LayoutInspectorTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/android-34",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/layout-inspector/testData",
        "//tools/base/build-system:gradle-distrib",
        "//prebuilts/studio/skiaparser/test_support",
    ],
    test_jvm_flags = select({
        "//tools/base/bazel/platforms:macos-arm64": ["-Djava.library.path=prebuilts/studio/skiaparser/test_support/mac-arm"],
        "//tools/base/bazel/platforms:macos-x86_64": ["-Djava.library.path=prebuilts/studio/skiaparser/test_support/mac"],
        "@platforms//os:windows": ["-Djava.library.path=prebuilts/studio/skiaparser/test_support/windows"],
        "@platforms//os:linux": ["-Djava.library.path=prebuilts/studio/skiaparser/test_support/linux"],
    }),
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:transport-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common[module]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api[module, test]",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide[module, test]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming.testUtil[module, test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module, test]",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module, test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector.integration",
    enable_tests = False,
    iml_files = ["intellij.android.layout-inspector.integration.iml"],
    test_resources = ["integration/testData"],
    test_srcs = ["integration/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "@intellij//:JUnit",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
    ],
)

layout_inspector_view_data = [
    ":view_project_deps",
    "//prebuilts/studio/sdk:build-tools/34.0.0",
    "//prebuilts/studio/sdk:emulator",
    "//prebuilts/studio/sdk:platform-tools",
    "//tools/adt/idea/layout-inspector/integration/testData:emptyApplication",
    INTEGRATION_TEST_GRADLE_VERSION,
    INTEGRATION_TEST_SYSTEM_IMAGE,
]

maven_repository(
    name = "view_project_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.application.com.android.application.gradle.plugin_8.2.2",
        "@maven//:com.android.library.com.android.library.gradle.plugin_8.2.2",
        "@maven//:com.android.tools.build.aapt2_8.2.2-10154469",
        "@maven//:com.android.tools.build.gradle_8.2.2",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

iml_studio_test(
    name = "LayoutInspectorViewIntegrationTest",
    timeout = "long",
    data = layout_inspector_view_data,
    module = ":intellij.android.layout-inspector.integration",
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.layoutinspector.LayoutInspectorViewIntegrationTest",
)

iml_studio_test(
    name = "EmbeddedLayoutInspectorIntegrationTest",
    timeout = "long",
    data = layout_inspector_view_data,
    module = ":intellij.android.layout-inspector.integration",
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.layoutinspector.EmbeddedLayoutInspectorIntegrationTest",
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector.gradle",
    srcs = ["gradle/src"],
    iml_files = ["gradle/intellij.android.layout-inspector.gradle.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector[module]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api[module]",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide[module]",
        "//tools/base/sdk-common/gradle:studio.android.sdktools.sdk-common.gradle[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)
