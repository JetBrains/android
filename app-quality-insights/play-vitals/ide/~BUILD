load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.ide",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-vitals.ide.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/app-quality-insights/ide:intellij.android.app-quality-insights.ide",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/app-quality-insights/play-vitals/view:intellij.android.app-quality-insights.play-vitals.view",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:intellij.android.app-quality-insights.play-vitals.model",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as",
        "//tools/adt/idea/.idea/libraries:javax-inject",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
        "@intellij//:intellij.libraries.grpc",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.ide.tests",
    iml_files = ["intellij.android.app-quality-insights.play-vitals.ide.tests.iml"],
    test_class = "com.android.tools.idea.vitals.VitalsIdeTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api",
        "//tools/adt/idea/app-quality-insights/ide:intellij.android.app-quality-insights.ide",
        "//tools/adt/idea/app-quality-insights/play-vitals/ide:intellij.android.app-quality-insights.play-vitals.ide",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as",
        "//tools/base/studio-grpc-testutils:studio.studio-grpc-testutils",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui",
        "//tools/adt/idea/app-quality-insights/play-vitals/view:intellij.android.app-quality-insights.play-vitals.view",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.java",
        "@intellij//:intellij.libraries.grpc",
    ],
)
