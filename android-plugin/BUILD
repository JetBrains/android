load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-plugin",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea:android[module]",
        "//tools/adt/idea/sdk-updates:sdk-updates[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:idea.gradle[module]",
        "//tools:idea.jetgroovy[module]",
    ],
)
