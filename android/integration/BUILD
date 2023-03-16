load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.integration",
    enable_tests = False,
    iml_files = ["intellij.android.core.integration.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
    ],
)

open_project_data = select({
    "@platforms//os:linux": [
        "//tools/adt/idea/studio:android-studio.linux.zip",
    ],
    "@platforms//os:macos": [
        "//tools/adt/idea/studio:android-studio.mac.zip",
    ],
    "@platforms//os:windows": [
        "//tools/adt/idea/studio:android-studio.win.zip",
    ],
}) + [
    ":minapp",
    ":openproject_deps",
    INTEGRATION_TEST_GRADLE_VERSION,
]

iml_test(
    name = "OpenProjectTest",
    timeout = "long",
    data = open_project_data,
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.OpenProjectTest",
)

iml_test(
    name = "OpenProjectWithThreadingAgentDisabledTest",
    timeout = "long",
    data = open_project_data,
    jvm_flags = ["-Dthreading_agent.disabled=true"],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.OpenProjectTest",
)

iml_test(
    name = "EmulatorTest",
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.EmulatorTest",
)

iml_test(
    name = "DebuggerTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
    }) + [
        ":buildproject_deps",
        ":minapp",
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_GRADLE_VERSION,
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.DebuggerTest",
)

iml_test(
    name = "AdbServerConnectionTest",
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags = ["manual"],  # b/243923041
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.AdbServerConnectionTest",
)

iml_test(
    name = "EmulatorBeforeStudioTest",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
    }) + [
        ":minapp",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/base/build-system:gradle-distrib-7.2",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.EmulatorBeforeStudioTest",
)

iml_test(
    name = "EmulatorAfterStudioTest",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
    }) + [
        ":minapp",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/base/build-system:gradle-distrib-7.2",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags_linux = [
        "block-network",
        "manual",  # b/271006743: emulator can't fully boot
    ],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.EmulatorAfterStudioTest",
)

iml_test(
    name = "LiveEditTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
        "//conditions:default": [],
    }) + [
        ":live_edit_project_deps",
        ":liveeditproject",
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/base/build-system:gradle-distrib-7.3.3",
        "@system_image_android-30_default_x86_64//:x86_64-android-30-images",
    ],
    module = ":intellij.android.core.integration",
    tags_linux = [
        "block-network",
        "very_flaky",  # b/263397644
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.LiveEditTest",
)

build_and_run_test_data = select({
    "@platforms//os:linux": [
        "//tools/adt/idea/studio:android-studio.linux.zip",
    ],
    "@platforms//os:macos": [
        "//tools/adt/idea/studio:android-studio.mac.zip",
    ],
    "@platforms//os:windows": [
        "//tools/adt/idea/studio:android-studio.win.zip",
    ],
    "//conditions:default": [],
}) + [
    ":buildproject_deps",
    ":minapp",
    "//prebuilts/studio/sdk:build-tools/30.0.3",
    "//prebuilts/studio/sdk:emulator",
    "//prebuilts/studio/sdk:platform-tools",
    INTEGRATION_TEST_GRADLE_VERSION,
    "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
]

iml_test(
    name = "BuildAndRunTest",
    timeout = "long",
    data = build_and_run_test_data,
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.BuildAndRunTest",
)

iml_test(
    name = "CreateProjectTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
    }) + [
        ":createproject_deps",
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        INTEGRATION_TEST_GRADLE_VERSION,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.CreateProjectTest",
)

maven_repository(
    name = "createproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.5.1",
        "@maven//:androidx.appcompat.appcompat_1.4.1",
        "@maven//:androidx.compose.compiler.compiler_1.3.2",
        "@maven//:androidx.compose.compose-bom_2022.10.00",
        "@maven//:androidx.compose.foundation.foundation-layout_1.3.0",
        "@maven//:androidx.compose.foundation.foundation_1.3.0",
        "@maven//:androidx.compose.material.material-icons-core_1.3.0",
        "@maven//:androidx.compose.material.material-ripple_1.3.0",
        "@maven//:androidx.compose.material.material_1.3.0",
        "@maven//:androidx.compose.material3.material3_1.0.0",
        "@maven//:androidx.compose.ui.ui-test-junit4_1.3.0",
        "@maven//:androidx.compose.ui.ui-test-manifest_1.3.0",
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.3.0",
        "@maven//:androidx.compose.ui.ui-tooling_1.3.0",
        "@maven//:androidx.compose.ui.ui_1.3.0",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.core.core-ktx_1.9.0",
        "@maven//:androidx.databinding.viewbinding_7.4.1",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.4.1",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.4.1",
        "@maven//:androidx.test.espresso.espresso-core_3.4.0",
        "@maven//:androidx.test.ext.junit_1.1.3",
        "@maven//:com.android.application.com.android.application.gradle.plugin_7.4.1",
        "@maven//:com.android.library.com.android.library.gradle.plugin_7.4.1",
        "@maven//:com.android.tools.build.aapt2_7.4.1-8841542",
        "@maven//:com.android.tools.build.gradle_7.4.1",
        "@maven//:com.google.android.material.material_1.5.0",
        "@maven//:com.google.protobuf.protobuf-bom_3.17.2",
        "@maven//:com.google.protobuf.protobuf-java_3.17.2",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.6.21",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.5.2",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

maven_repository(
    name = "openproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

iml_test(
    name = "BuildProjectTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
    }) + [
        ":buildproject_deps",
        ":minapp",
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        INTEGRATION_TEST_GRADLE_VERSION,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.BuildProjectTest",
)

iml_test(
    name = "RunApkTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
        "//conditions:default": [],
    }) + [
        ":helloworldapk",
        "//prebuilts/studio/sdk:platforms/android-31",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/base/build-system:gradle-distrib-7.2",
        "@system_image_android-31_default_x86_64//:x86_64-android-31-images",
    ],
    module = ":intellij.android.core.integration",
    tags = ["manual"],  # Flaky due to b/260867011
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.RunApkTest",
)

iml_test(
    name = "BuildAndRunInstrumentedTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
        "//conditions:default": [],
    }) + [
        ":run_instrumented_test_project_deps",
        ":instrumenttestapp",
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/base/build-system:gradle-distrib-7.5",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.BuildAndRunInstrumentedTest",
)

maven_repository(
    name = "buildproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.aapt2_7.1.0-7984345",
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "live_edit_project_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.5.1",
        "@maven//:androidx.compose.compiler.compiler_1.3.2",
        "@maven//:androidx.compose.material.material_1.3.0",
        "@maven//:androidx.compose.ui.ui-test-manifest_1.3.0",
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.3.0",
        "@maven//:androidx.compose.ui.ui-tooling_1.3.0",
        "@maven//:androidx.compose.ui.ui_1.3.0",
        "@maven//:androidx.core.core-ktx_1.9.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.3.1",
        "@maven//:com.android.tools.build.aapt2_7.1.0-7984345",
        "@maven//:com.android.tools.build.aapt2_7.2.0-7984345",
        "@maven//:com.android.tools.build.gradle_7.2.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

maven_repository(
    name = "run_instrumented_test_project_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.appcompat.appcompat_1.4.1",
        "@maven//:androidx.core.core-ktx_1.8.0",
        "@maven//:androidx.test.espresso.espresso-core_3.4.0",
        "@maven//:androidx.test.ext.junit_1.1.3",
        "@maven//:com.android.tools.build.aapt2_7.2.0-7984345",
        "@maven//:com.android.tools.build.gradle_7.2.0",
        "@maven//:com.android.tools.emulator.proto_30.2.0",
        "@maven//:com.android.tools.utp.android-device-provider-ddmlib_30.2.0",
        "@maven//:com.android.tools.utp.android-device-provider-gradle_30.2.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-additional-test-output_30.2.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-coverage_30.2.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-device-info-proto_30.2.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-device-info_30.2.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-logcat_30.2.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-retention_30.2.0",
        "@maven//:com.android.tools.utp.android-test-plugin-result-listener-gradle-proto_30.2.0",
        "@maven//:com.android.tools.utp.android-test-plugin-result-listener-gradle_30.2.0",
        "@maven//:com.google.android.material.material_1.5.0",
        "@maven//:com.google.testing.platform.android-device-provider-local_0.0.8-alpha07",
        "@maven//:com.google.testing.platform.android-driver-instrumentation_0.0.8-alpha07",
        "@maven//:com.google.testing.platform.android-test-plugin_0.0.8-alpha07",
        "@maven//:com.google.testing.platform.core-proto_0.0.8-alpha08",
        "@maven//:com.google.testing.platform.core_0.0.8-alpha07",
        "@maven//:com.google.testing.platform.launcher_0.0.8-alpha07",
        "@maven//:javax.annotation.javax.annotation-api_1.3.2",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-jvm_1.5.2",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.5.2",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "liveeditproject",
    srcs = glob(["testData/liveedit/**"]),
)

filegroup(
    name = "helloworldapk",
    srcs = glob(["testData/helloworldapk/**"]),
)

filegroup(
    name = "instrumenttestapp",
    srcs = glob(["testData/InstrumentedTestApp/**"]),
)
