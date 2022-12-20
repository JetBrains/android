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
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module]",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb.ui.tests",
    iml_files = ["intellij.android.adb.ui.tests.iml"],
    test_class = "com.android.tools.idea.ui.AdbUiTestSuite",
    test_friends = ["//tools/adt/idea/android-adb-ui:intellij.android.adb.ui"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/adblib:studio.android.sdktools.adblib[module, test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)
