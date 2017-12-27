load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "observable-ui",
    srcs = ["src"],
    iml_files = ["observable-ui.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/adt-ui[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:Guava[test]",
    ],
)
