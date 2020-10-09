load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.lint",
    srcs = ["src"],
    iml_files = ["intellij.lint.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.lint.tests",
    iml_files = ["tests/intellij.lint.tests.iml"],
    project = "unb",
    test_class = "com.android.tools.idea.lint.common.LintIdeTestSuite",
    test_data = [
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/lint/tests/testData",
        "//tools/adt/idea/android/annotations",
        "//tools/idea/java/mockJDK-1.7",
    ],
    test_resources = ["tests/testData"],
    test_srcs = ["tests/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
    ],
)
