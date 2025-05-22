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
    test_friends = ["//tools/adt/idea/android-adb-ui:intellij.android.adb.ui"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "@intellij//:com.intellij.platform.images[test]",
        "@intellij//:intellij.webp[test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[test]",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
