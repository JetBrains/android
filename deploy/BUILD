load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.deploy",
    srcs = ["src"],
    iml_files = ["intellij.android.deploy.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_data = glob(["testData/**"]) + ["//prebuilts/studio/sdk:platform-tools"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/tracer:studio.android.sdktools.tracer[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/build-common:intellij.android.buildCommon[module]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)
