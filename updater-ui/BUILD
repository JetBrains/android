load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "updater-ui",
    srcs = ["src"],
    iml_files = ["updater-ui.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/updater[module]",
        "//tools/base/repository:studio.repository[module]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/adt/idea/android[module]",
    ],
)
