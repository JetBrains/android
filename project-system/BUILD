load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "project-system",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["project-system.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/platform/annotations/common:annotations-common[module]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
    ],
)

iml_module(
    name = "project-system-tests",
    iml_files = ["project-system-tests.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/project-system[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/base/sdk-common:studio.sdk-common[module, test]",
    ],
)
