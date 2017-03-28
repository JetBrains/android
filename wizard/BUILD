load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "wizard",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools:idea.platform-impl_and_others[module]",
    ],
)
