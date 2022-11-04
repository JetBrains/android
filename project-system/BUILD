load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # testSrc here is for related test infrastructure, not tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)
