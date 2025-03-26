load("@exec_properties//:constants.bzl", "RBE_CPU_8")
load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "INTEGRATION_TEST_SYSTEM_IMAGE")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer",
    srcs = ["src"],
    iml_files = ["intellij.android.device-explorer.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/device-explorer-common:intellij.android.device-explorer-common",
        "//tools/adt/idea/device-explorer-files:intellij.android.device-explorer-files",
        "//tools/adt/idea/device-explorer-monitor:intellij.android.device-explorer-monitor",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer.tests",
    iml_files = ["intellij.android.device-explorer.tests.iml"],
    test_class = "com.android.tools.idea.device.explorer.DeviceExplorerTestSuite",
    test_data = [
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/device-explorer-common:intellij.android.device-explorer-common",
        "//tools/adt/idea/device-explorer:intellij.android.device-explorer",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android-common:intellij.android.common.tests",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer.integration.tests",
    enable_tests = False,
    iml_files = ["intellij.android.device-explorer.integration.tests.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/device-explorer:intellij.android.device-explorer",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
    test_srcs = ["integration"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)

iml_test(
    name = "DeviceExplorerIntegrationTest",
    timeout = "long",
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/adt/idea/android/integration:buildproject_deps",
        "//tools/adt/idea/android/integration:minapp",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
    exec_properties = RBE_CPU_8,
    module = ":intellij.android.device-explorer.integration.tests",
    tags = [
        "studio-e2e-test",
    ],
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "testSrc.com.android.tools.idea.device.explorer.DeviceExplorerIntegrationTest",
)
