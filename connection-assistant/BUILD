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
        "//tools/adt/idea/assistant[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/usb-devices:studio.usb-devices[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/analytics[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "connection-assistant-test",
    iml_files = ["connection-assistant-test.iml"],
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.connection.assistant.ConnectionAssistantTestSuite",
    test_data = [
        "//prebuilts/studio/jdk/jdk11",
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
        "//tools/adt/idea/assistant[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/usb-devices:studio.usb-devices[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/connection-assistant[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)
