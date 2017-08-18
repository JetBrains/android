load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-lang",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["android-lang.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.lang.AndroidLangTestSuite",
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/adt/idea/artwork[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea:java-analysis-impl[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)
