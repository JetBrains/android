load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "profilers-ui",
    srcs = ["src"],
    iml_files = ["profilers-ui.iml"],
    tags = ["managed"],
    test_data = glob(["testData/**"]),
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
        "//tools/adt/idea/profilers[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui[module]",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/base/perflib:studio.perflib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/adt/idea/artwork[module]",
    ],
)
