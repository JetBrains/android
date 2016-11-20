load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-branding",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.util[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:idea.compiler-impl_and_others[module]",
    ],
)
