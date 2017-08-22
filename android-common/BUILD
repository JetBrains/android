load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-common",
    srcs = ["src"],
    iml_files = ["android-common.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.AndroidCommonTestSuite",
    test_data = [
        "//tools/adt/idea/adt-ui/lib/libwebp",
        "//tools/adt/idea/artwork:device-art-resources",
    ],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/idea/platform/util[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/adt/idea/adt-ui[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea:platform-impl_and_others[module, test]",
    ],
)
