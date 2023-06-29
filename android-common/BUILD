load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.common",
    srcs = ["src"],
    generate_k2_tests = True,
    iml_files = ["intellij.android.common.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.AndroidCommonTestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/android-common/testData",
    ],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/ninepatch:studio.android.sdktools.ninepatch[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/server-flags:intellij.android.server-flags[module]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
    ],
)
