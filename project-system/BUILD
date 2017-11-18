load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "project-system",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["project-system.iml"],
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
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea:platform-impl[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/platform/annotations/common:annotations-common[module]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
    ],
)

iml_module(
    name = "project-system-tests",
    iml_files = ["project-system-tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:lvcs-impl",
        "//tools/idea:json",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:testFramework[module, test]",
        "//tools/idea:compiler-impl_and_others[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/project-system[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/base/sdk-common:studio.sdk-common[module, test]",
    ],
)
