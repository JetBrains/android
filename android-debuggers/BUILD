load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-debuggers",
    srcs = ["src"],
    iml_files = ["android-debuggers.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
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
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/adt/idea/adt-branding",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/.idea/libraries:sqlite"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-debuggers[module, test]",
        "//tools/adt/idea/android-adb[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jre8[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
