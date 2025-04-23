load("@exec_properties//:constants.bzl", "RBE_CPU_8")
load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "INTEGRATION_TEST_SYSTEM_IMAGE")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
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
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testingSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/deploy:intellij.android.deploy",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/wizard:intellij.android.wizard",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common",
        "//tools/adt/idea/app-inspection/api:app-inspection.api",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/streaming:intellij.android.streaming",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/analytics",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/adt/idea/device-manager-v2:intellij.android.device-manager-v2",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector.tests",
    iml_files = ["intellij.android.layout-inspector.tests.iml"],
    test_class = "com.android.tools.idea.layoutinspector.LayoutInspectorTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/android-35",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/skiaparser/test_support",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/layout-inspector/testData",
        "//tools/base/build-system:gradle-distrib",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/app-inspection/api:app-inspection.api",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/deploy:intellij.android.deploy",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/streaming:intellij.android.streaming",
        "//tools/adt/idea/streaming:intellij.android.streaming.testUtil",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector.gradle",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/device-manager-v2:intellij.android.device-manager-v2",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
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
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector.integration",
    enable_tests = False,
    iml_files = ["intellij.android.layout-inspector.integration.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/base/common:studio.android.sdktools.common",
    ],
    test_resources = ["integration/testData"],
    test_srcs = ["integration/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:JUnit",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/testutils:studio.android.sdktools.testutils",
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

iml_test(
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

iml_test(
    name = "EmbeddedLayoutInspectorIntegrationTest",
    timeout = "long",
    data = layout_inspector_view_data,
    exec_properties = RBE_CPU_8,
    module = ":intellij.android.layout-inspector.integration",
    tags = ["studio-e2e-test"],
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
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector",
        "//tools/adt/idea/app-inspection/api:app-inspection.api",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide",
        "//tools/base/sdk-common/gradle:studio.android.sdktools.sdk-common.gradle",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
    ],
)
