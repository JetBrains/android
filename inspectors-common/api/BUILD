load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.inspectors-common.api",
    srcs = ["src"],
    iml_files = ["intellij.android.inspectors-common.api.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_class = "com.android.tools.inspectors.common.api.InspectorsCommonApiTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/codenavigation[module]",
    ],
)
