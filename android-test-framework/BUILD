load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-test-framework",
    iml_files = ["android-test-framework.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
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
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/xml/xml-psi-api[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/adt/idea/android[module, test]",
        "//tools/adt/idea/android-common[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jsr305[test]",
        "//tools/idea:dom-openapi[module, test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)
