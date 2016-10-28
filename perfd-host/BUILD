# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "perfd-host",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    test_resources = ["src/test/resources"],
    deps = [
        "//tools/adt/idea/adt-ui:adt-ui",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/base/ddmlib:studio.ddmlib",
        "//tools:idea.annotations[module]",
        "//tools/base/rpclib:studio.rpclib[module]",
        "//tools/idea/.idea/libraries:profiler-grpc-java",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
