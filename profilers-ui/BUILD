load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "profilers-ui",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/profilers:profilers[module]",
        "//tools:idea.annotations[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools:idea.core-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui:adt-ui[module]",
        "//tools:idea.platform-api[module]",
        "//tools/adt/idea/adt-ui-model:adt-ui-model[module]",
    ],
)
