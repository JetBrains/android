load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "kotlin-integration-tests",
    iml_files = ["kotlin-integration-tests.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.KotlinIntegrationTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-for-studio",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/android/lib:androidWidgets",
        "//tools/adt/idea/android/lib/libwebp",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/kotlin-integration/testData",
        "//tools/base/templates",
        "//tools/idea:platform-build-info",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android[module, test]",
        "//tools/idea/uast/uast-common[module, test]",
        "//tools/idea/java/openapi[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea/java/java-psi-impl[module, test]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/adt/idea/android-plugin[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-branding[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
