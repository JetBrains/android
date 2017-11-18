load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

iml_module(
    name = "android-adb",
    srcs = ["src"],
    iml_files = ["android-adb.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:Guava",
    ],
)

iml_module(
    name = "android-adb-tests",
    iml_files = ["android-adb-tests.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.adb.AndroidAdbTestSuite",
    test_data = ["//prebuilts/studio/sdk:platform-tools"],
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
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-adb[module, test]",
        "//tools/base/ddmlib:studio.ddmlib[module, test]",
        "//tools/idea:testFramework[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
