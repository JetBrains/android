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
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
