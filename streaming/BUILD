load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.streaming",
    srcs = ["src"],
    iml_files = ["intellij.android.streaming.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = ["//tools/adt/idea/.idea/libraries:ffmpeg-platform"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/analytics",
        "//tools/analytics-library/crash:analytics-crash",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
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
    split_test_targets = {
        "benchmark": {
            "test_filter": "com.android.tools.idea.streaming.benchmark",
        },
        "core": {
            "test_filter": "com.android.tools.idea.streaming.core",
        },
        "device": {
            "test_filter": "com.android.tools.idea.streaming.device",
        },
        "emulator": {
            "test_filter": "com.android.tools.idea.streaming.emulator",
        },
        "other": {},
    },
    test_class = "com.android.tools.idea.streaming.StreamingTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/streaming/native:libimage_converter.prebuilt",
        "//tools/adt/idea/streaming/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/analytics-library/crash:analytics-crash",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-adb:intellij.android.adb.testUtil",
        "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/streaming:intellij.android.streaming",
        "//tools/adt/idea/streaming:intellij.android.streaming.testUtil",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:emulator-proto",
        "//tools/adt/idea/.idea/libraries:ffmpeg",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:jimfs",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "@intellij//:com.intellij.platform.images",
        "@intellij//:intellij.webp",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
    ],
    test_friends = ["//tools/adt/idea/streaming:intellij.android.streaming"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/.idea/libraries:ffmpeg-platform"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.streaming.integration",
    enable_tests = False,  # Integration tests have to run alone
    iml_files = ["intellij.android.streaming.integration.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android/integration:intellij.android.core.integration",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/streaming:intellij.android.streaming",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
    test_friends = ["//tools/adt/idea/streaming:intellij.android.streaming"],
    test_srcs = ["integration"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//tools/base/adblib:studio.android.sdktools.adblib"],
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
        "ci:studio-linux_very_flaky",  # b/303195243
        "noci:studio-linux",
        "noci:studio-win",
    ],
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.streaming.device.ScreenSharingAgentTestSuite",
)

iml_test(
    name = "EmulatorUiSettingsIntegrationTest",
    size = "large",  # Involves starting an emulator, and running a test on that emulator
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/streaming/integration/languages",
        "//tools/adt/idea/streaming/integration/languages:resource-files",
        "//tools/adt/idea/streaming/screen-sharing-agent",
        "//tools/adt/idea/streaming/screen-sharing-agent:native/x86_64/libscreen-sharing-agent.so",
        "@system_image_android-35_default_x86_64//:x86_64-android-35-images",
    ],
    module = ":intellij.android.streaming.integration",
    tags = [
        "ci:studio-linux_very_flaky",  # b/303195243
        "noci:studio-linux",
        "noci:studio-win",
    ],
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.streaming.uisettings.EmulatorUiSettingsIntegrationTestSuite",
)

iml_test(
    name = "DeviceUiSettingsIntegrationTest",
    size = "large",  # Involves starting an emulator, and running a test on that emulator
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/streaming/integration/languages",
        "//tools/adt/idea/streaming/integration/languages:resource-files",
        "//tools/adt/idea/streaming/screen-sharing-agent",
        "//tools/adt/idea/streaming/screen-sharing-agent:native/x86_64/libscreen-sharing-agent.so",
        "@system_image_android-35_default_x86_64//:x86_64-android-35-images",
    ],
    module = ":intellij.android.streaming.integration",
    tags = [
        "ci:studio-linux_very_flaky",  # b/271008444
        "noci:studio-linux",
        "noci:studio-win",
    ],
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.streaming.uisettings.DeviceUiSettingsIntegrationTestSuite",
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.streaming.testUtil",
    iml_files = ["intellij.android.streaming.testUtil.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-adb:intellij.android.adb.testUtil",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/streaming:intellij.android.streaming",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:emulator-proto",
        "//tools/adt/idea/.idea/libraries:ffmpeg",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
    ],
    test_friends = ["//tools/adt/idea/streaming:intellij.android.streaming"],
    test_srcs = ["testUtil"],
    test_tags = ["manual"],  # testUtil is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/.idea/libraries:ffmpeg-platform"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)
