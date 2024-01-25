load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "kotlin-integration-tests",
    generate_k2_tests = True,
    iml_files = ["kotlin-integration-tests.iml"],
    test_class = "com.android.tools.idea.KotlinIntegrationTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)
