load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uitest-framework-bazel",
    iml_files = ["uitest-framework-bazel.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
        "//tools/vendor/intellij/cidr:doxygen",
        "//tools/idea:community-main",
        "//tools/idea/jps/javac-ref-scanner-8",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/uitest-framework[module, test]",
        "//tools:fest-swing[module, test]",
        "//tools/idea/platform/external-system-api[module, test]",
        "//tools/idea/java/openapi[module, test]",
        "//tools/idea/platform/platform-impl[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)
