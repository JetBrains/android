load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-policy.ide",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-policy.ide.iml"],
    resources = ["resources"],
    test_class = "com.android.tools.idea.play.PlayPolicyTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/app-quality-insights/ide:intellij.android.app-quality-insights.ide",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/lint:intellij.lint",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/vendor/google/play-client/metadata:intellij.android.play-client.metadata",
        "//tools/vendor/google/login:google-login",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/adt/idea/android-lint:intellij.android.lint",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
    ],
)
