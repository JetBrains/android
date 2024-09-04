load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-whs",
    srcs = ["src"],
    iml_files = ["intellij.android.wear-whs.iml"],
    resources = ["resources"],
    test_class = "com.android.tools.idea.wearwhs.WearHealthServicesTestSuite",
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
        "@intellij//:test-framework[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
