load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.android.artwork",
    # do not sort: must match IML order
    srcs = [
        "resources",
        "src",
        "gen",
    ],
    bundle_data = [":device-art-resources"],
    iml_files = ["intellij.android.artwork.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//tools/idea/platform/util:intellij.platform.util[module]"],
)

filegroup(
    name = "device-art-resources",
    srcs = glob(["resources/device-art-resources/**"]),
    visibility = ["//visibility:public"],
)
