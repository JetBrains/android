load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-designer",
    srcs = ["src"],
    generate_k1_tests = True,
    iml_files = ["intellij.android.wear-designer.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/wear-preview-elements:intellij.android.wear-preview-elements",
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
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/adt/idea/wear-designer:intellij.android.wear-designer",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/designer:intellij.android.designer.tests",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer.tests",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/wear-preview-elements:intellij.android.wear-preview-elements",
    ],
    test_friends = ["//tools/adt/idea/wear-designer:intellij.android.wear-designer"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:com.intellij.java",
    ],
)
