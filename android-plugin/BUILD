load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-plugin",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    deps = [
        "//tools/adt/idea:android[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:idea.gradle[module]",
        "//tools:idea.jetgroovy[module]",
    ],
    visibility = ["//visibility:public"],
)
