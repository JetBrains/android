load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.ide",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.ide.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui[module]",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.ide.tests",
    iml_files = ["intellij.android.app-quality-insights.ide.tests.iml"],
    test_class = "com.android.tools.idea.insights.InsightsIdeTestSuite",
    test_srcs = ["tests"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/app-quality-insights/ide:intellij.android.app-quality-insights.ide[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
    ],
)
