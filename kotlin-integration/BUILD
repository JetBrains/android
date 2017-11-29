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
        "//tools/adt/idea/adt-ui/lib/libwebp",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/kotlin-integration/testData",
        "//tools/base/templates",
        "//tools/idea:platform-build-info",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/maven:maven2-server-impl",
        "//tools/vendor/intellij/cidr:doxygen",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "manual",
        "ij2017.3",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/adt-branding",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android[module, test]",
        "//tools/idea/uast/uast-common[module, test]",
        "//tools/idea/java/openapi[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea/java/java-psi-impl[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/adt/idea/android-plugin[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
