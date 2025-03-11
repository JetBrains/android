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
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/glance-designer:intellij.android.glance-designer",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/intellij.android.compose-common",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_friends = ["//tools/adt/idea/glance-designer:intellij.android.glance-designer"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)
