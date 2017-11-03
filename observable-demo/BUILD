load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "observable-demo",
    srcs = ["src"],
    iml_files = ["observable-demo.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/observable[module]",
        "//tools/adt/idea/observable-ui[module]",
        "//tools/adt/idea/adt-ui[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
