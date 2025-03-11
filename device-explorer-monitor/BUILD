load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer-monitor",
    srcs = ["src"],
    iml_files = ["intellij.android.device-explorer-monitor.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/device-explorer-common:intellij.android.device-explorer-common",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
        "//tools/base/backup:studio.android.sdktools.backup",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/backup:intellij.android.backup.api",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/base/environment-services:studio.intellij.android.environment-services",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/adblib-ddmlibcompatibility:studio.android.sdktools.adblib.ddmlibcompatibility",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer-monitor.tests",
    iml_files = ["intellij.android.device-explorer-monitor.tests.iml"],
    test_class = "com.android.tools.idea.device.explorer.monitor.DeviceMonitorTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/device-explorer-monitor:intellij.android.device-explorer-monitor",
        "//tools/adt/idea/device-explorer-common:intellij.android.device-explorer-common",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android-common:intellij.android.common.tests",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/adblib-ddmlibcompatibility:studio.android.sdktools.adblib.ddmlibcompatibility",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)
