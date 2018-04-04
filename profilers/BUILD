load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.android.profilers",
    srcs = ["src"],
    iml_files = ["intellij.android.profilers.iml"],
    tags = ["managed"],
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/adt/idea/profilers-atrace[module]",
    ],
)
