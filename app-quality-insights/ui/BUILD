load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.ui.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:test-framework[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/ml-api:intellij.android.ml-api[module]",
        "//prebuilts/tools/common/m2:commonmark-0.22.0",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.ui.tests",
    iml_files = ["intellij.android.app-quality-insights.ui.tests.iml"],
    test_class = "com.android.tools.idea.insights.ui.InsightsUiTestSuite",
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module, test]",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/ml-api:intellij.android.ml-api[module, test]",
        "//prebuilts/tools/common/m2:commonmark-0.22.0[test]",
    ],
)
