load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.execution.common",
    srcs = ["src"],
    iml_files = ["intellij.android.execution.common.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.execution.common.tests",
    iml_files = ["intellij.android.execution.common.tests.iml"],
    test_class = "com.android.tools.idea.execution.common.AndroidExecutionCommonTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module]",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
    ],
)
