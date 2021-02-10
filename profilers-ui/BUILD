load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.profilers.ui.iml"],
    lint_baseline = "lint_baseline.xml",
    test_class = "com.android.tools.profilers.ProfilersUiTestSuite",
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/profilers:intellij.android.profilers[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
    ],
)
