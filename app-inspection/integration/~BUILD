load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "INTEGRATION_TEST_SYSTEM_IMAGE", "KOTLIN_ARTIFACTS_FOR_TESTS")
load("//tools/adt/idea/studio:studio.bzl", "studio_project_model")
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
        ":minapp_deps",
        ":minapp_project_model",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
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
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
)

studio_project_model(
    name = "minapp_project_model",
    srcs = [
        ":minapp",
        ":minapp_deps",
        "//tools/base/build-system:gradle-distrib-8.6",
    ],
    manifest_path = "tools/adt/idea/app-inspection/integration/minapp_deps.manifest",
    out_dir_name = "minapp_project_model",
    project_path = "tools/adt/idea/app-inspection/integration/testData/minapp",
    visibility = ["//visibility:public"],
)
