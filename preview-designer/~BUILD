load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.preview-designer",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.android.preview-designer.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/adt/idea/preview-fast-compile:intellij.android.preview-fast-compile",
        "//tools/adt/idea/completion:intellij.android.completion",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.preview-designer.tests",
    generate_k1_tests = True,
    iml_files = ["intellij.android.preview-designer.tests.iml"],
    split_test_targets = {
        # this target does some performance tests and takes a long time to run
        "AnnotationsGraphTest": {
            "test_filter": "com.android.tools.idea.preview.find.AnnotationsGraphTest",
        },
        "other": {
            "shard_count": 2,
        },
    },
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.preview.PreviewDesignerTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/compose-ide-plugin/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/rendering:intellij.android.rendering.tests",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/adt/idea/preview-fast-compile:intellij.android.preview-fast-compile",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/adt/idea/designer:intellij.android.designer.tests",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:asm-tools",
    ],
    test_friends = ["//tools/adt/idea/preview-designer:intellij.android.preview-designer"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)
