load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-debuggers",
    srcs = ["src"],
    iml_files = ["android-debuggers.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/base/flags:studio.flags[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/adt/idea/android-adb[module]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)

iml_module(
    name = "android-debuggers-tests",
    iml_files = ["android-debuggers-tests.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.debuggers.AndroidDebuggersTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-debuggers[module, test]",
        "//tools/adt/idea/android-adb[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jre8[test]",
        "//tools/idea/.idea/libraries:sqlite",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
