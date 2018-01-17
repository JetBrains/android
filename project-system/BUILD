load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "project-system",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["project-system.iml"],
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
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/platform/annotations/common:annotations-common[module]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
    ],
)

iml_module(
    name = "project-system-tests",
    iml_files = ["project-system-tests.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.projectsystem.ProjectSystemTestSuite",
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/project-system[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/base/sdk-common:studio.sdk-common[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
