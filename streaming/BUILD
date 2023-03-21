load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.streaming",
    srcs = ["src"],
    iml_files = ["intellij.android.streaming.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = ["//tools/adt/idea/.idea/libraries:ffmpeg-platform"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/analytics[module]",
        "//tools/analytics-library/crash:analytics-crash[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//tools/adt/idea/.idea/libraries:emulator-proto",
        "//tools/adt/idea/.idea/libraries:ffmpeg",
        "//tools/adt/idea/.idea/libraries:HdrHistogram",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.streaming.tests",
    iml_files = ["intellij.android.streaming.tests.iml"],
    test_class = "com.android.tools.idea.streaming.StreamingTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/streaming/native:libimage_converter.prebuilt",
        "//tools/adt/idea/streaming/testData",
    ],
    test_friends = ["//tools/adt/idea/streaming:intellij.android.streaming"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/analytics-library/crash:analytics-crash[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb.testUtil[module, test]",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming.testUtil[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:emulator-proto[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-webp[test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.streaming.integration",
    enable_tests = False,  # Integration tests have to run alone
    iml_files = ["intellij.android.streaming.integration.iml"],
    test_friends = ["//tools/adt/idea/streaming:intellij.android.streaming"],
    test_srcs = ["integration"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

iml_test(
    name = "ScreenSharingAgentTest",
    size = "large",  # Involves starting an emulator, and running multiple tests on that emulator
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/adt/idea/streaming/integration/event-logger",
        "//tools/adt/idea/streaming/screen-sharing-agent",
        "//tools/adt/idea/streaming/screen-sharing-agent:native/x86_64/libscreen-sharing-agent.so",
        "@system_image_android-30_default_x86_64//:x86_64-android-30-images",
    ],
    module = ":intellij.android.streaming.integration",
    tags = [
        "no_mac",
        "no_windows",
        "very_flaky",  # b/271008444
    ],
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.streaming.device.ScreenSharingAgentTestSuite",
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.streaming.testUtil",
    iml_files = ["intellij.android.streaming.testUtil.iml"],
    test_friends = ["//tools/adt/idea/streaming:intellij.android.streaming"],
    test_srcs = ["testUtil"],
    test_tags = ["manual"],  # testUtil is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/.idea/libraries:ffmpeg-platform"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:emulator-proto[test]",
        "//tools/adt/idea/.idea/libraries:ffmpeg[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb.testUtil[module, test]",
    ],
)
