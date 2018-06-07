load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "wizard-model",
    srcs = ["src"],
    iml_files = ["wizard-model.iml"],
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
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)
