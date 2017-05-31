load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "sdk-updates",
    srcs = ["src"],
    iml_files = ["sdk-updates.iml"],
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
        "//tools/idea/platform/platform-impl[module]",
        "//tools/adt/idea/android[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/analytics-library/protos:analytics-protos[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/adt/idea/adt-ui[module]",
        "//tools/adt/idea/wizard[module]",
        "//tools/adt/idea/wizard-model[module]",
        "//tools/adt/idea/observable-ui[module]",
    ],
)

iml_module(
    name = "sdk-updates-tests",
    iml_files = ["sdk-updates-tests.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.updater.SdkUpdaterTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/base/templates",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/adt/idea/adt-branding",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/sdk-updates[module, test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/base/repository:studio.repository[module, test]",
        "//tools/base/sdklib:studio.sdklib[module, test]",
        "//tools/adt/idea/android[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
