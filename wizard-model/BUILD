load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "wizard-model",
    srcs = ["src"],
    iml_files = ["wizard-model.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.wizard.WizardModelTestSuite",
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:lvcs-impl",
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:relaxng",
        "//tools/idea:json",
        "//tools/idea:images",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
