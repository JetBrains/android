load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sdkUpdates",
    srcs = ["src"],
    iml_files = ["intellij.android.sdkUpdates.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/observable:intellij.android.observable",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/wizard:intellij.android.wizard",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/base/flags:studio.android.sdktools.flags",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sdkUpdates.tests",
    iml_files = ["intellij.android.sdkUpdates.tests.iml"],
    tags = ["noci:studio-win"],
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
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[test]",
        "//tools/base/repository:studio.android.sdktools.repository[test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
    ],
)
