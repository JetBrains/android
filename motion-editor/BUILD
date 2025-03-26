load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.motion-editor",
    srcs = ["src"],
    iml_files = ["intellij.android.motion-editor.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    tags = [
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.uibuilder.handlers.motion.MotionTestSuite",
    test_data = [
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:build-tools/latest",  # Required by all SceneTest
        "//prebuilts/studio/sdk:platforms/latest",  # Required by all SceneTest
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/motion-editor/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/designer:intellij.android.designer.tests",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
    ],
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/.idea/libraries:layoutlib",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose",
        "//tools/base/flags:studio.android.sdktools.flags",
    ],
)
