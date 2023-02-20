load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sdkUpdates",
    srcs = ["src"],
    iml_files = ["intellij.android.sdkUpdates.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sdkUpdates.tests",
    iml_files = ["intellij.android.sdkUpdates.tests.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.updater.SdkUpdaterTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
    ],
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/base/repository:studio.android.sdktools.repository[module, test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
    ],
)
