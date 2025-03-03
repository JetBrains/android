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
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard",
        "//tools/adt/idea/android-templates:intellij.android.templates",
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
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/android-templates:intellij.android.templates[test]",
        "//tools/adt/idea/assistant[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/observable:intellij.android.observable[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin",
    ],
)
