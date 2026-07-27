load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "INTEGRATION_TEST_SYSTEM_IMAGE", "KOTLIN_ARTIFACTS_FOR_TESTS")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-policy.integration",
    enable_tests = False,
    iml_files = ["intellij.android.app-quality-insights.play-policy.integration.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/as-driver:as-driver.utils",
        "@intellij//:intellij-sdk",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
)

iml_test(
    name = "PlayPolicyInsightIntegrationTest",
    timeout = "moderate",
    data = [
        ":minapp",
        ":minapp_deps",
        "//prebuilts/studio/sdk:build-tools/36.0.0",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
        "//prebuilts/studio/jdk:jdk21",
    ],
    module = ":intellij.android.app-quality-insights.play-policy.integration",
    tags = ["studio-e2e-test"],
    tags_linux = [
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.play.test.PlayPolicyInsightIntegrationTest",
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
