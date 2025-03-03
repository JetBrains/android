load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer-files",
    srcs = ["src"],
    iml_files = ["intellij.android.device-explorer-files.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/device-explorer-common:intellij.android.device-explorer-common",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
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
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/device-explorer-files:intellij.android.device-explorer-files[test]",
        "//tools/adt/idea/device-explorer-common:intellij.android.device-explorer-common[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/android-common:intellij.android.common.tests[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
