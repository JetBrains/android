load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.ide",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.ide.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/android:intellij.android.core",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/analytics",
        "@intellij//:intellij.platform.vcs.impl",
        "@intellij//:intellij.platform.vcs.dvcs.impl",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.ide.tests",
    iml_files = ["intellij.android.app-quality-insights.ide.tests.iml"],
    split_test_targets = {
        "VcsIntegrationTest": {
            "test_filter": "com.android.tools.idea.insights.vcs.VcsIntegrationTest",
            "data": [
                "//tools/base/build-system:android_gradle_plugin.zip",
                "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
                "//tools/base/build-system:gradle-distrib",
            ],
        },
        "other": {},
    },
    test_class = "com.android.tools.idea.insights.InsightsIdeTestSuite",
    test_data = [
        ":testData",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/app-quality-insights/ide:intellij.android.app-quality-insights.ide[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "@intellij//:org.jetbrains.kotlin[test]",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[test]",
        "//tools/analytics-library/tracker:analytics-tracker[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[test]",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui.tests[test]",
        "//tools/vendor/google/git4insights[test]",
        "@intellij//:Git4Idea[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "@intellij//:intellij.platform.vcs.impl[test]",
        "@intellij//:intellij.platform.vcs.dvcs.impl[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)

filegroup(
    name = "testData",
    srcs = glob(["testData/**"]),
    visibility = ["//visibility:public"],
)
