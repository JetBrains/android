load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.glance-designer",
    srcs = ["src"],
    iml_files = ["intellij.android.glance-designer.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/intellij.android.compose-common",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.glance-designer.tests",
    iml_files = ["intellij.android.glance-designer.tests.iml"],
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.glance.preview.GlanceDesignerTestSuite",
    test_friends = ["//tools/adt/idea/glance-designer:intellij.android.glance-designer"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/designer:intellij.android.designer[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/adt/idea/glance-designer:intellij.android.glance-designer[test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[test]",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/intellij.android.compose-common[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
