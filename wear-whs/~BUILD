load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-whs",
    srcs = ["src"],
    iml_files = ["intellij.android.wear-whs.iml"],
    resources = ["resources"],
    test_class = "com.android.tools.idea.wearwhs.WearHealthServicesTestSuite",
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_deps = [
        "@intellij//:test-framework",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/streaming:intellij.android.streaming.testUtil",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/streaming:intellij.android.streaming",
        "//tools/analytics-library/tracker:analytics-tracker",
    ],
)
