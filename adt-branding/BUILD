load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-branding",
    srcs = ["src"],
    bundle_data = glob(["**"]),
    iml_files = ["adt-branding.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:compiler-impl_and_others[module]",
    ],
)
