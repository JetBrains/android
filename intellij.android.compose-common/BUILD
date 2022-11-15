load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-common",
    srcs = ["src"],
    iml_files = ["intellij.android.compose-common.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-common.tests",
    iml_files = ["intellij.android.compose-common.tests.iml"],
    test_class = "com.android.tools.compose.ComposeCommonTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/intellij.android.compose-common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
)
