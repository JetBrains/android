load("//tools/base/bazel:bazel.bzl", "iml_module")

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
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:kotlinx-coroutines-guava",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.logcat.tests",
    iml_files = ["intellij.android.logcat.tests.iml"],
    test_class = "com.android.tools.idea.LogcatTestSuite",
    test_friends = ["//tools/adt/idea/logcat:intellij.android.logcat"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/logcat:intellij.android.logcat[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/adblib-ddmlibcompatibility:studio.android.sdktools.adblib.ddmlibcompatibility[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module, test]",
    ],
)
