load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.screenshot-test",
    srcs = ["src"],
    iml_files = ["intellij.android.screenshot-test.iml"],
    kotlin_use_compose = True,
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.screenshot-test.tests",
    iml_files = ["intellij.android.screenshot-test.tests.iml"],
    test_class = "com.android.screenshottest.run.ScreenshotTestingTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/screenshot-testing/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_for_compose_prebuilts",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[test]",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "@intellij//:JUnit[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/screenshot-testing:intellij.android.screenshot-test",
    ],
)
