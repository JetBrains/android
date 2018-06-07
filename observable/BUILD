load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "observable",
    srcs = ["src"],
    iml_files = ["observable.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/xml/relaxng",
        "//tools/idea/json",
        "//tools/idea/images",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework[module, test]",
    ],
)
