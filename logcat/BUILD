load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.logcat",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.android.logcat.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    test_resources = ["testResources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/process-monitor:studio.android.sdktools.process-monitor",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/logcat:logcat-proto",
        "//prebuilts/r8",
        "@intellij//:intellij.json.split",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.logcat.tests",
    iml_files = ["intellij.android.logcat.tests.iml"],
    test_class = "com.android.tools.idea.LogcatTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:sources",
    ],
    test_friends = ["//tools/adt/idea/logcat:intellij.android.logcat"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/logcat:logcat-proto",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:intellij.json.split[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/analytics-library/tracker:analytics-tracker[test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[test]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[test]",
        "//tools/base/adblib-ddmlibcompatibility:studio.android.sdktools.adblib.ddmlibcompatibility[test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/base/process-monitor:studio.android.sdktools.process-monitor[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/logcat:intellij.android.logcat[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "logcat-proto",
    jars = ["//tools/adt/idea/logcat/proto:logcat-proto.jar"],
    visibility = ["//visibility:public"],
)
