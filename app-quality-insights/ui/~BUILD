load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.ui.iml"],
    # do not sort: must match IML order
    test_deps = [
        "@intellij//:test-framework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/.idea/libraries:mockito",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
        "@intellij//:intellij.platform.vcs.impl",
        "@intellij//:intellij.platform.vcs.log.impl",
        "@intellij//:intellij.platform.vcs.dvcs.impl",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as",
        "@intellij//:intellij.libraries.grpc",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.ui.tests",
    iml_files = ["intellij.android.app-quality-insights.ui.tests.iml"],
    test_class = "com.android.tools.idea.insights.ui.InsightsUiTestSuite",
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api",
        "//tools/adt/idea/app-quality-insights/ui:intellij.android.app-quality-insights.ui",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "@intellij//:intellij.platform.vcs.impl",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij.libraries.grpc",
    ],
)
