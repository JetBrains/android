load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "INTEGRATION_TEST_SYSTEM_IMAGE", "KOTLIN_ARTIFACTS_FOR_TESTS")
load("//tools/adt/idea/emulator-snapshots:emulator_test_env.bzl", "emulator_snapshot_data", "emulator_snapshot_jvm_flags")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.integration",
    enable_tests = False,
    iml_files = ["app-inspection.integration.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)

iml_test(
    name = "LaunchInspectorsTest",
    timeout = "long",
    data = [
        ":minapp",
        ":minapp_deps",
        "//prebuilts/studio/sdk:build-tools/36.0.0",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
        "//prebuilts/studio/jdk:jdk21",
    ] + emulator_snapshot_data(INTEGRATION_TEST_SYSTEM_IMAGE),
    jvm_flags = emulator_snapshot_jvm_flags(INTEGRATION_TEST_SYSTEM_IMAGE),
    module = ":app-inspection.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.appinspection.LaunchInspectorsTest",
)

maven_repository(
    name = "minapp_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.application.com.android.application.gradle.plugin_9.0.0",
        "@maven//:com.android.tools.build.aapt2_9.0.0-14304508",
        "@maven//:com.android.tools.build.gradle_9.0.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
)
