load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "wizard",
    srcs = ["src"],
    iml_files = ["wizard.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # no tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/adt/idea/wizard-model"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/wizard-model[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/adt/idea/observable-ui[module]",
    ],
)
