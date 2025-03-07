load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wizard",
    srcs = ["src"],
    iml_files = ["intellij.android.wizard.iml"],
    lint_baseline = "lint_baseline.xml",
    test_tags = ["manual"],  # no tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
        "//tools/adt/idea/observable:intellij.android.observable",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/artwork:intellij.android.artwork",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wizard.tests",
    iml_files = ["intellij.android.wizard.tests.iml"],
    test_class = "com.android.tools.idea.wizard.WizardTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/wizard:intellij.android.wizard[test]",
        "@intellij//:intellij-sdk[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[test]",
    ],
)
