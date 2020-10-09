load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.profilers.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.profilers.ui.iml"],
    lint_baseline = "lint_baseline.xml",
    project = "unb",
    test_class = "com.android.tools.profilers.ProfilersUiTestSuite",
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/profilers:intellij.android.profilers[module]",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto",
        "//tools/adt/idea/.idea/libraries:unb.transport-proto",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
    ],
)
