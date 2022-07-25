load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers.atrace",
    # do not sort: must match IML order
    srcs = [
        "src/core/common/src/main/kotlin",
        "src/core/model/src/main/kotlin",
    ],
    iml_files = ["intellij.android.profilers.atrace.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)
