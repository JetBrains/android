load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-designer",
    srcs = ["src"],
    generate_k2_tests = True,
    iml_files = ["intellij.android.wear-designer.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer[module]",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements[module]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-designer.tests",
    iml_files = ["intellij.android.wear-designer.tests.iml"],
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.wear.preview.WearDesignerTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:sources",
    ],
    test_friends = ["//tools/adt/idea/wear-designer:intellij.android.wear-designer"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer[module, test]",
        "//tools/adt/idea/wear-designer:intellij.android.wear-designer[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer.tests[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
    ],
)
