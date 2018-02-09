load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "assistant",
    srcs = ["src"],
    iml_files = ["assistant.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/adt/idea/adt-branding",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/adt/idea/android[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/adt/idea/artwork[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/plugins/groovy/groovy-psi[module]",
    ],
)

iml_module(
    name = "assistant-test",
    iml_files = ["assistant-test.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.assistant.view.AssistantTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/base/templates",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/adt/idea/adt-branding",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/adt/idea/android[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/adt/idea/artwork[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/plugins/groovy/groovy-psi[module]",
        "//tools/adt/idea/assistant[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
