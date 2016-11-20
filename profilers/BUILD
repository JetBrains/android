load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "profilers",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.annotations[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/base/perflib:studio.perflib[module]",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)
