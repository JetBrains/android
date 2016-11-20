load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "perfd-host",
    srcs = ["src/main/java"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.platform-api[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools:idea.annotations-common[module]",
        "//tools/base/rpclib:studio.rpclib[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
    ],
)
