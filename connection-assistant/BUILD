load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "connection-assistant",
    srcs = ["src"],
    iml_files = ["connection-assistant.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/assistant",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/usb-devices:studio.usb-devices",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/analytics",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "connection-assistant-test",
    iml_files = ["connection-assistant-test.iml"],
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.connection.assistant.ConnectionAssistantTestSuite",
    test_data = [
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/assistant",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/usb-devices:studio.usb-devices",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/connection-assistant[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
