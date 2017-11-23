load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "observable-ui",
    srcs = ["src"],
    iml_files = ["observable-ui.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:testExtensions",
        "//tools/idea:lvcs-impl",
        "//tools/idea:xml",
        "//tools/idea:spellchecker",
        "//tools/idea:relaxng",
        "//tools/idea:json",
        "//tools/idea:images",
        "//tools/idea:xdebugger-impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/adt-ui[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:Guava[test]",
    ],
)
