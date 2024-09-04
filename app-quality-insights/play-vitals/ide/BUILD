load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.ide",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-vitals.ide.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/app-quality-insights/ide:intellij.android.app-quality-insights.ide[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/app-quality-insights/play-vitals/view:intellij.android.app-quality-insights.play-vitals.view[module]",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui[module]",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module]",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:intellij.android.app-quality-insights.play-vitals.model[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as[module]",
        "//tools/adt/idea/.idea/libraries:javax-inject",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.ide.tests",
    iml_files = ["intellij.android.app-quality-insights.play-vitals.ide.tests.iml"],
    test_class = "com.android.tools.idea.vitals.VitalsIdeTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module, test]",
        "//tools/adt/idea/app-quality-insights/ide:intellij.android.app-quality-insights.ide[module, test]",
        "//tools/adt/idea/app-quality-insights/play-vitals/ide:intellij.android.app-quality-insights.play-vitals.ide[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as[module, test]",
        "//tools/base/studio-grpc-testutils:studio.studio-grpc-testutils[module, test]",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui[module, test]",
        "//tools/adt/idea/app-quality-insights/play-vitals/view:intellij.android.app-quality-insights.play-vitals.view[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
    ],
)
