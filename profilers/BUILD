load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "profilers",
    srcs = ["src"],
    test_srcs = ["testSrc"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.annotations[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui:adt-ui[module]",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)
