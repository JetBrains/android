load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-dsl-parser",
    srcs = ["src"],
    iml_files = ["gradle-dsl-parser.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.gradle.dsl.GradleDslParserTestSuite",
    test_runtime_deps = [
        "//tools/adt/idea/android-plugin",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/adt/idea/android[module]",
        "//tools/idea/plugins/groovy/groovy-psi[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/project-system[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:Gradle",
    ],
)
