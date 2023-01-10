load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer",
    srcs = ["src"],
    iml_files = ["intellij.android.device-explorer.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/device-explorer-files:intellij.android.device-explorer-files[module]",
        "//tools/adt/idea/device-explorer-monitor:intellij.android.device-explorer-monitor[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer.tests",
    iml_files = ["intellij.android.device-explorer.tests.iml"],
    test_class = "com.android.tools.idea.device.explorer.DeviceExplorerTestSuite",
    test_data = [
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/device-explorer:intellij.android.device-explorer[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/device-explorer-files:intellij.android.device-explorer-files[module, test]",
        "//tools/adt/idea/device-explorer-monitor:intellij.android.device-explorer-monitor[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module, test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer.integration.tests",
    enable_tests = False,
    iml_files = ["intellij.android.device-explorer.integration.tests.iml"],
    test_srcs = ["integration"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/device-explorer:intellij.android.device-explorer[module, test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

iml_test(
    name = "DeviceExplorerIntegrationTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
        "//conditions:default": [],
    }) + [
        "//tools/adt/idea/android/integration:buildproject_deps",
        "//tools/adt/idea/android/integration:minapp",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_GRADLE_VERSION,
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.device-explorer.integration.tests",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "testSrc.com.android.tools.idea.device.explorer.DeviceExplorerIntegrationTest",
)
