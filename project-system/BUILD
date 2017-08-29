load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "project-system",
    # do not sort: must match IML order
    srcs = [
        "src",
        "testSrc",
    ],
    iml_files = ["project-system.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
    ],
)
