load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "connection-assistant",
    srcs = ["src"],
    iml_files = ["connection-assistant.iml"],
    tags = ["managed"],
    # keep sorted
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/base/templates",
    ],
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
        "//tools/adt/idea/assistant[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/analytics-library/protos:analytics-protos[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/base/usb-devices:studio.usb-devices[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/adt/idea/android[module]",
        "//tools/adt/idea/android-adb[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/base/flags:studio.flags[module]",
    ],
)

iml_module(
    name = "connection-assistant-test",
    iml_files = ["connection-assistant-test.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.connection.assistant.ConnectionAssistantTestSuite",
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
        "//tools/adt/idea/assistant[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/analytics-library/protos:analytics-protos[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/base/usb-devices:studio.usb-devices[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/adt/idea/android[module]",
        "//tools/adt/idea/android-adb[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/base/flags:studio.flags[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/adt/idea/connection-assistant[module, test]",
    ],
)
