load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-debuggers",
    srcs = ["src"],
    iml_files = ["android-debuggers.iml"],
    tags = ["managed"],
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
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
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
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea/plugins/maven:maven2-server-impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/.idea/libraries:sqlite"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-debuggers[module, test]",
        "//tools/adt/idea/android-adb[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea:testFramework[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jre8[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
