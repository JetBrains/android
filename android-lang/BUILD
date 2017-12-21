load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-lang",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
        "resources",
    ],
    iml_files = ["android-lang.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
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
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/adt/idea/artwork[module]",
        "//tools/idea:java-analysis-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/uast/uast-common[module]",
        "//tools/idea/platform/util[module]",
    ],
)

iml_module(
    name = "android-lang-tests",
    iml_files = ["android-lang-tests.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.lang.AndroidLangTestSuite",
    test_data = glob(["testData/**"]) + [
        "//tools/adt/idea/android/annotations",
        "//tools/base/templates",
        "//tools/idea/java/mockJDK-1.7",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/maven:maven2-server-impl",
        "//tools/adt/idea/adt-branding",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/plugins/IntelliLang:IntelliLang-java",
        "//tools/idea/plugins/IntelliLang:IntelliLang-xml",
        "//tools/idea/uast/uast-java",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-lang[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/adt/idea/android[module, test]",
    ],
)
