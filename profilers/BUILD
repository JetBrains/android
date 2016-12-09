load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "profilers",
    srcs = ["src"],
    tags = ["managed"],
    test_data = ["testData"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/perflib:studio.perflib[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)
