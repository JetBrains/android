load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "artwork",
    # do not sort: must match IML order
    srcs = [
        "resources",
        "src",
        "gen",
    ],
    iml_files = ["artwork.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//tools/idea/platform/util[module]"],
)

filegroup(
    name = "device-art-resources",
    srcs = glob(["resources/device-art-resources/**"]),
    visibility = ["//visibility:public"],
)
