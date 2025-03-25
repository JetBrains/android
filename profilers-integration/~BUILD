load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers.integration",
    enable_tests = False,
    iml_files = ["intellij.android.profilers.integration.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

profilers_test_common_data = [
    ":minapp",
    ":minapp_deps",
    "//prebuilts/studio/sdk:build-tools/33.0.1",
    "//prebuilts/studio/sdk:emulator",
    "//prebuilts/studio/sdk:platform-tools",
    INTEGRATION_TEST_GRADLE_VERSION,
    "@system_image_android-33_default_x86_64//:x86_64-android-33-images",
]

iml_test(
    name = "ProfileWithCompleteDataTest",
    timeout = "long",
    data = profilers_test_common_data + [
        "@system_image_android-31_default_x86_64//:x86_64-android-31-images",
    ],
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/322952709
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.sessionbased.ProfileWithCompleteDataTest",
)

iml_test(
    name = "ProfileWithLowOverheadTest",
    timeout = "long",
    data = profilers_test_common_data + [
        "@system_image_android-33PlayStore_default_x86_64//:x86_64-android-33PlayStore-images",
    ],
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/282881259, b/260867011
        "block-network",
    ],
    tags_mac = ["manual"],  # b/282881259, b/260867011: Enable the tests in MAC once the flakiness is resolved in linux and moved to pre-submits.
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.sessionbased.ProfileWithLowOverheadTest",
)

iml_test(
    name = "SystemTraceTest",
    timeout = "long",
    data = profilers_test_common_data + [
        "@system_image_android-33PlayStore_default_x86_64//:x86_64-android-33PlayStore-images",
    ],
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
        "block-network",
    ],
    tags_mac = ["manual"],  # b/260867011: Enable the tests in MAC once the flakiness is resolved in linux and moved to pre-submits.
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.sessionbased.SystemTraceTest",
)

iml_test(
    name = "CallstackSampleTest",
    timeout = "long",
    data = profilers_test_common_data + [
        "@system_image_android-31_default_x86_64//:x86_64-android-31-images",
    ],
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
        "block-network",
    ],
    tags_mac = ["manual"],  # b/260867011: Enable the tests in MAC once the flakiness is resolved in linux and moved to pre-submits.
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.sessionbased.CallstackSampleTest",
)

iml_test(
    name = "HeapDumpTest",
    timeout = "long",
    data = profilers_test_common_data + [
        "@system_image_android-33PlayStore_default_x86_64//:x86_64-android-33PlayStore-images",
    ],
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
        "block-network",
    ],
    tags_mac = ["manual"],  # b/260867011: Enable the tests in MAC once the flakiness is resolved in linux and moved to pre-submits.
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.sessionbased.HeapDumpTest",
)

iml_test(
    name = "NativeAllocationsTest",
    timeout = "long",
    data = profilers_test_common_data + [
        "@system_image_android-31_default_x86_64//:x86_64-android-31-images",
    ],
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
        "block-network",
    ],
    tags_mac = ["manual"],  # b/260867011: Enable the tests in MAC once the flakiness is resolved in linux and moved to pre-submits.
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.sessionbased.NativeAllocationsTest",
)

iml_test(
    name = "SystemTraceTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.SystemTraceTaskTest",
)

iml_test(
    name = "HeapDumpTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
        "block-network",
    ],
    tags_mac = ["manual"],  # b/260867011: Enable the tests in MAC once the flakiness is resolved in linux and moved to pre-submits.
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.HeapDumpTaskTest",
)

iml_test(
    name = "CallstackSampleTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.CallstackSampleTaskTest",
)

iml_test(
    name = "NativeAllocationsTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.NativeAllocationsTaskTest",
)

iml_test(
    name = "JavaKotlinMethodRecordingTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = [],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.JavaKotlinMethodRecordingTaskTest",
)

iml_test(
    name = "LiveViewTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.LiveViewTaskTest",
)

iml_test(
    name = "JavaKotlinAllocationsTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
        "block-network",
    ],
    tags_mac = ["manual"],  # b/260867011: Enable the tests in MAC once the flakiness is resolved in linux and moved to pre-submits.
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.JavaKotlinAllocationsTaskTest",
)

iml_test(
    name = "StartupSystemTraceTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/369429660
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.StartupSystemTraceTaskTest",
)

iml_test(
    name = "StartupJavaKotlinMethodRecordingTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/369429660
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.StartupJavaKotlinMethodRecordingTaskTest",
)

iml_test(
    name = "StartupNativeAllocationsTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/369429660
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.StartupNativeAllocationsTaskTest",
)

iml_test(
    name = "SessionBasedProfilingWithApkTest",
    timeout = "long",
    data = profilers_test_common_data + [
        ":helloworldapk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/android-31",
    ],
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "block-network",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
    ],
    tags_windows = [
        # TODO(b/287284693): Add Windows support for this test.
        "noci:studio-win",
    ],
    test_class = "com.android.tools.profilers.integration.sessionbased.SessionBasedProfilingWithApkTest",
)

iml_test(
    name = "TaskBasedProfilingWithApkTest",
    timeout = "long",
    data = profilers_test_common_data + [
        ":helloworldapk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/android-31",
    ],
    module = ":intellij.android.profilers.integration",
    tags_linux = [
        "block-network",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
    ],
    tags_windows = [
        # TODO(b/287284693): Add Windows support for this test.
        "noci:studio-win",
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.TaskBasedProfilingWithApkTest",
)

iml_test(
    name = "ImportAndExportTraceTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.ImportAndExportTraceTest",
)

maven_repository(
    name = "minapp_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:com.android.tools.lint.lint-gradle_30.4.1",
        "@maven//:com.google.protobuf.protobuf-bom_3.17.2",
        "@maven//:com.google.protobuf.protobuf-java-util_3.17.2",
        "@maven//:com.google.protobuf.protobuf-java_3.17.2",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.10",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
)

filegroup(
    name = "helloworldapk",
    srcs = glob(["testData/helloworldapk/**"]),
)
