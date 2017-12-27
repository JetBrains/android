load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "wizard-model",
    srcs = ["src"],
    iml_files = ["wizard-model.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.wizard.WizardModelTestSuite",
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
    ],
)
