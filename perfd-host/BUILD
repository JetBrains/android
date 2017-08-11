load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "perfd-host",
    srcs = ["src/main/java"],
    iml_files = ["perfd-host.iml"],
    tags = ["managed"],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/idea/platform/annotations/common:annotations-common[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:sqlite",
        "//tools/analytics-library/protos:analytics-protos[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
    ],
)
