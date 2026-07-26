load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "KOTLIN_ARTIFACTS_FOR_TESTS")
load("//tools/adt/idea/emulator-snapshots:emulator_test_env.bzl", "emulator_snapshot_data", "emulator_snapshot_jvm_flags")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers.integration",
    enable_tests = False,
    iml_files = ["intellij.android.profilers.integration.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)

profilers_test_common_data_base = [
    ":minapp",
    ":minapp_deps",
    "//prebuilts/studio/sdk:build-tools/36.0.0",
    "//prebuilts/studio/sdk:emulator",
    "//prebuilts/studio/sdk:platform-tools",
    "//prebuilts/studio/sdk:system_image_android-33_default",
    INTEGRATION_TEST_GRADLE_VERSION,
]

profilers_test_common_data = profilers_test_common_data_base + emulator_snapshot_data("//prebuilts/studio/sdk:system_image_android-33_default")

iml_test(
    name = "ProfileWithCompleteDataTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.ProfileWithCompleteDataTest",
)

iml_test(
    name = "ProfileWithLowOverheadTest",
    timeout = "long",
    data = profilers_test_common_data_base + [
        "//prebuilts/studio/sdk:system_image_android-33PlayStore_default",
    ],
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
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
    test_class = "com.android.tools.profilers.integration.taskbased.ProfileWithLowOverheadTest",
)

iml_test(
    name = "SystemTraceTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.SystemTraceTaskTest",
)

iml_test(
    name = "SystemTraceInEditorTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.SystemTraceInEditorTaskTest",
)

iml_test(
    name = "HeapDumpTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
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
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
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
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
        "block-network",
    ],
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
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.JavaKotlinMethodRecordingTaskTest",
)

iml_test(
    name = "JavaKotlinMethodRecordingInEditorTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.JavaKotlinMethodRecordingInEditorTaskTest",
)

iml_test(
    name = "LiveViewTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.LiveViewTaskTest",
)

iml_test(
    name = "LeakCanaryTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.LeakCanaryTaskTest",
)

iml_test(
    name = "JavaKotlinAllocationsTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
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
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
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
    name = "StartupSystemTraceInEditorTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.StartupSystemTraceInEditorTaskTest",
)

iml_test(
    name = "StartupJavaKotlinMethodRecordingTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
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
    name = "StartupJavaKotlinMethodRecordingInEditorTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.StartupJavaKotlinMethodRecordingInEditorTaskTest",
)

iml_test(
    name = "StartupNativeAllocationsTaskTest",
    timeout = "long",
    data = profilers_test_common_data,
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
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
    name = "TaskBasedProfilingWithApkTest",
    timeout = "long",
    data = profilers_test_common_data + [
        ":helloworldapk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/android-31",
    ],
    jvm_flags = emulator_snapshot_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
        "block-network",
    ],
    tags_windows = [
        # TODO(b/287284693): Add Windows support for this test.
        "noci:studio-win",
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.TaskBasedProfilingWithApkTest",
)

iml_test(
    name = "TaskBasedProfilingWithApkInEditorTest",
    timeout = "long",
    data = profilers_test_common_data + [
        ":helloworldapk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/android-31",
    ],
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
        "block-network",
    ],
    tags_windows = [
        "noci:studio-win",
    ],
    test_class = "com.android.tools.profilers.integration.taskbased.TaskBasedProfilingWithApkInEditorTest",
)

iml_test(
    name = "ImportAndExportTraceTest",
    timeout = "long",
    data = profilers_test_common_data_base,
    module = ":intellij.android.profilers.integration",
    tags = ["studio-e2e-test"],
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
        "@maven//:com.android.application.com.android.application.gradle.plugin_9.0.0",
        "@maven//:com.android.tools.build.aapt2_9.0.0-14304508",
        "@maven//:com.android.tools.build.gradle_9.0.0",
        "@maven//:com.android.tools.lint.lint-gradle_32.0.0",
        "@maven//:com.google.protobuf.protobuf-bom_3.17.2",
        "@maven//:com.google.protobuf.protobuf-java-util_3.17.2",
        "@maven//:com.google.protobuf.protobuf-java_3.17.2",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
)

filegroup(
    name = "helloworldapk",
    srcs = glob(["testData/helloworldapk/**"]),
)
