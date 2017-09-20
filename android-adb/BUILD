load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

iml_module(
    name = "android-adb",
    srcs = ["src"],
    iml_files = ["android-adb.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.adb.AndroidAdbTestSuite",
    test_data = ["//prebuilts/studio/sdk:platform-tools"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)
