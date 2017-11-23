load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-dsl-parser",
    srcs = ["src"],
    iml_files = ["gradle-dsl-parser.iml"],
    manual_test_runtime_deps = [
        "//tools/adt/idea/android-plugin",
    ],
    tags = ["managed"],
    test_class = "com.android.tools.idea.gradle.dsl.GradleDslParserTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea/plugins/maven:maven2-server-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/adt/idea/android[module]",
        "//tools/idea/plugins/groovy/groovy-psi[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/project-system[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:Gradle",
    ],
)
