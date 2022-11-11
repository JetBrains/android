load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer-files",
    srcs = ["src"],
    iml_files = ["intellij.android.device-explorer-files.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:kotlinx-coroutines-guava",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer-files.tests",
    iml_files = ["intellij.android.device-explorer-files.tests.iml"],
    test_agents = ["//tools/base/threading-agent:threading_agent.jar"],
    test_class = "com.android.tools.idea.device.explorer.files.DeviceExplorerTestSuite",
    # keep sorted
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
        "//tools/adt/idea/device-explorer-files:intellij.android.device-explorer-files[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module, test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module, test]",
    ],
)
