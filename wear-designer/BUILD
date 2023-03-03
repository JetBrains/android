load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-designer",
    srcs = ["src"],
    iml_files = ["intellij.android.wear-designer.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-designer.tests",
    iml_files = ["intellij.android.wear-designer.tests.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.wear.preview.WearDesignerTestSuite",
    test_friends = ["//tools/adt/idea/wear-designer:intellij.android.wear-designer"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer[module, test]",
        "//tools/adt/idea/wear-designer:intellij.android.wear-designer[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
    ],
)
