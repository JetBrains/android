load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-debuggers",
    srcs = ["src"],
    iml_files = ["android-debuggers.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.debuggers.AndroidDebuggersTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/.idea/libraries:sqlite",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/base/flags:studio.flags[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/adt/idea/android-adb[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jre8",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)
