load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "artwork",
    srcs = [
        "gen",
        "resources",
        "src",
    ],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
    ],
)
