load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

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
        "@intellij//:com.intellij.platform.images",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/analytics",
        "//tools/adt/idea/testartifacts:intellij.android.testartifacts",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.screenshot-test.tests",
    iml_files = ["intellij.android.screenshot-test.tests.iml"],
    test_class = "com.android.screenshottest.run.ScreenshotTestingTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData:projects",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/screenshot-testing/gradle:intellij.android.screenshot-test.gradle",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:JUnit",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/testartifacts:intellij.android.testartifacts",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/screenshot-testing:intellij.android.screenshot-test",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.google.guava.guava_19.0",
    ],
)
