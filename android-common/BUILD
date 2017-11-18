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
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
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
        "//tools/idea:platform-impl[module, test]",
        "//tools/base/ninepatch:studio.ninepatch[module]",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/base/flags:studio.flags[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
