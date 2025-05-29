load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.adb.ui.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.platform.images",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb.ui.tests",
    iml_files = ["intellij.android.adb.ui.tests.iml"],
    test_class = "com.android.tools.idea.ui.AdbUiTestSuite",
    test_data = [
        "//tools/adt/idea/android-adb-ui/testData",
        "//tools/adt/idea/artwork:device-art-resources",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:com.intellij.platform.images",
        "@intellij//:intellij.webp",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_friends = ["//tools/adt/idea/android-adb-ui:intellij.android.adb.ui"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
)
