load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer.customview",
    srcs = ["src"],
    iml_files = ["intellij.android.designer.customview.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer.customview.tests",
    iml_files = ["intellij.android.designer.customview.tests.iml"],
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.customview.preview.CustomViewTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/designer/customview/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/adt/idea/designer/customview:intellij.android.designer.customview",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/rendering:intellij.android.rendering",
    ],
    test_resources = ["testData"],
    test_shard_count = 2,
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
        "cpu:3",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/android:intellij.android.core.tests",
    ],
)

maven_repository(
    name = "test_deps",
    artifacts = [
        "@maven//:androidx.annotation.annotation_1.5.0",
        "@maven//:androidx.localbroadcastmanager.localbroadcastmanager_1.0.0",
    ],
)
