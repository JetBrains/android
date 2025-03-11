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
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:intellij-sdk",
        "@intellij//:intellij.json.split",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
        "//tools/base/adblib-ddmlibcompatibility:studio.android.sdktools.adblib.ddmlibcompatibility",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/process-monitor:studio.android.sdktools.process-monitor",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/logcat:intellij.android.logcat",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_friends = ["//tools/adt/idea/logcat:intellij.android.logcat"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/logcat:logcat-proto",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "logcat-proto",
    jars = ["//tools/adt/idea/logcat/proto:logcat-proto.jar"],
    visibility = ["//visibility:public"],
)
