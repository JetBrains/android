load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "observable",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea:platform-impl_and_others[module]",
    ],
)
