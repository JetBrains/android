load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.android.rt",
    srcs = ["src"],
    iml_files = ["intellij.android.rt.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
    ],
)
