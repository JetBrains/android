load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "smali",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["smali.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.smali.SmaliTestSuite",
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:lvcs-impl",
        "//tools/idea:json",
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
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
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea:compiler-impl_and_others[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
