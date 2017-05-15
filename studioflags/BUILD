load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studioflags",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/flags:studio.flags",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/flags:studio.flags[module]",
    ],
)
