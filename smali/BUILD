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
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
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
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
