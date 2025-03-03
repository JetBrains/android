load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.view",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-vitals.view.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:intellij.android.app-quality-insights.play-vitals.model",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.view.tests",
    iml_files = ["intellij.android.app-quality-insights.play-vitals.view.tests.iml"],
    test_class = "com.android.tools.idea.vitals.ui.VitalsUiTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "@intellij//:test-framework[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/app-quality-insights/play-vitals/view:intellij.android.app-quality-insights.play-vitals.view[test]",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:intellij.android.app-quality-insights.play-vitals.model[test]",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[test]",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui.tests[test]",
    ],
)
