load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "profilers-ui",
    srcs = ["src"],
    iml_files = ["profilers-ui.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/profilers[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui[module]",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/base/perflib:studio.perflib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/adt/idea/artwork[module]",
    ],
)
