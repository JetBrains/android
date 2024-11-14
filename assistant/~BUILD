load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "assistant",
    srcs = ["src"],
    iml_files = ["assistant.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.intellij.groovy",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module]",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[module]",
        "//tools/adt/idea/android-templates:intellij.android.templates[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "assistant-test",
    iml_files = ["assistant-test.iml"],
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.assistant.view.AssistantTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-templates:intellij.android.templates[module, test]",
        "//tools/adt/idea/assistant[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/observable:intellij.android.observable[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
    ],
)
