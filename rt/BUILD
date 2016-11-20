load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-rt",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
    ],
)
