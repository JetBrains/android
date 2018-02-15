load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "native-symbolizer",
    # do not sort: must match IML order
    srcs = [
        "src",
    ],
    iml_files = ["native-symbolizer.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.nativeSymbolizer.NativeSymbolizerTestSuite",
    test_data = glob(["testData/**"]) + [
        "//prebuilts/tools:lldb",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "short",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/core-api[module]",
        "//tools/adt/idea/android[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/vendor/intellij/cidr/cidr-debugger[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
        "//tools/idea/platform/core-impl[module]",
    ],
)
