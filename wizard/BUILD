load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "wizard",
    srcs = ["src"],
    iml_files = ["wizard.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # no tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/adt/idea/wizard-model"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/wizard-model[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/idea:platform-impl[module, test]",
        "//tools/adt/idea/observable-ui[module]",
    ],
)
