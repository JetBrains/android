load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.dagger",
    srcs = ["src"],
    iml_files = ["intellij.android.dagger.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.dagger.tests",
    iml_files = ["intellij.android.dagger.tests.iml"],
    project = "unb",
    tags = [
        "no_test_windows",  # b/135665870
    ],
    test_class = "com.android.tools.tests.AndroidDaggerTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:unb.truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/dagger:intellij.android.dagger[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
    ],
)
