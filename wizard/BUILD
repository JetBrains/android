load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "wizard",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/adt/idea/wizard-model"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/wizard-model[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)
