load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lint",
    srcs = ["src"],
    iml_files = ["intellij.android.lint.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/lint:intellij.lint[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lint.tests",
    iml_files = ["intellij.android.lint.tests.iml"],
    test_class = "com.android.tools.idea.lint.AndroidLintTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/repository:studio.android.sdktools.repository[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[module, test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module, test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
    ],
)
