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
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
        "//tools/adt/idea/adt-branding",
        "//tools/idea/java/compiler/instrumentation-util-8",
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
        "//tools/idea/platform/platform-impl[module, test]",
        "//tools/base/ninepatch:studio.ninepatch[module]",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/base/flags:studio.flags[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
