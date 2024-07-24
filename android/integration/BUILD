load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "INTEGRATION_TEST_SYSTEM_IMAGE", "KOTLIN_ARTIFACTS_FOR_TESTS")
load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.integration",
    enable_tests = False,
    iml_files = ["intellij.android.core.integration.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
    ],
)

open_project_data = [
    ":minapp",
    ":openproject_deps",
    INTEGRATION_TEST_GRADLE_VERSION,
]

iml_studio_test(
    name = "OpenProjectTest",
    timeout = "long",
    data = open_project_data,
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.OpenProjectTest",
)

iml_studio_test(
    name = "OpenProjectWithThreadingAgentDisabledTest",
    timeout = "long",
    data = open_project_data,
    jvm_flags = ["-Dthreading_agent.disabled=true"],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.OpenProjectTest",
)

iml_studio_test(
    name = "ShowUsagesTest",
    timeout = "long",
    data = [
        ":minapp",
        ":showusages_deps",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.ShowUsagesTest",
)

editor_performance_test_data = [
    ":editor_performance_test_deps",
    ":architectureSamples",
    ":architectureSamplesScenarioScripts",
    "//prebuilts/studio/sdk:build-tools/30.0.3",
    "//prebuilts/studio/sdk:platform-tools",
    "//tools/base/build-system:gradle-distrib-8.6",
]

maven_repository(
    name = "editor_performance_test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.6.1",
        "@maven//:androidx.arch.core.core-testing_2.1.0",
        "@maven//:androidx.compose.animation.animation-core_1.3.3",
        "@maven//:androidx.compose.compiler.compiler_1.4.3",
        "@maven//:androidx.compose.compose-bom_2023.01.00",
        "@maven//:androidx.compose.foundation.foundation-layout_1.3.1",
        "@maven//:androidx.compose.foundation.foundation_1.3.1",
        "@maven//:androidx.compose.runtime.runtime-saveable_1.3.3",
        "@maven//:androidx.compose.runtime.runtime_1.3.3",
        "@maven//:androidx.compose.ui.ui-test-junit4_1.3.3",
        "@maven//:androidx.compose.ui.ui-test-manifest_1.3.3",
        "@maven//:androidx.compose.ui.ui-text_1.3.3",
        "@maven//:androidx.compose.ui.ui-tooling_1.3.3",
        "@maven//:androidx.compose.ui.ui-util_1.3.3",
        "@maven//:androidx.lifecycle.lifecycle-livedata_2.6.0-alpha03",
        "@maven//:androidx.lifecycle.lifecycle-runtime-compose_2.6.0-alpha03",
        "@maven//:androidx.lifecycle.lifecycle-runtime-testing_2.6.0-alpha03",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-compose_2.6.0-alpha03",
        "@maven//:androidx.navigation.navigation-compose_2.5.3",
        "@maven//:androidx.navigation.navigation-testing_2.5.3",
        "@maven//:androidx.room.room-compiler_2.5.0-rc01",
        "@maven//:androidx.room.room-ktx_2.5.0-rc01",
        "@maven//:androidx.room.room-testing_2.5.0-rc01",
        "@maven//:androidx.test.core-ktx_1.5.0",
        "@maven//:androidx.test.espresso.espresso-contrib_3.5.0",
        "@maven//:androidx.test.espresso.espresso-intents_3.5.0",
        "@maven//:androidx.test.espresso.idling.idling-concurrent_3.5.0",
        "@maven//:androidx.test.ext.junit-ktx_1.1.4",
        "@maven//:com.android.application.com.android.application.gradle.plugin_7.4.0",
        "@maven//:com.android.library.com.android.library.gradle.plugin_7.4.0",
        "@maven//:com.android.tools.build.gradle_7.4.0",
        "@maven//:com.google.accompanist.accompanist-appcompat-theme_0.28.0",
        "@maven//:com.google.accompanist.accompanist-swiperefresh_0.28.0",
        "@maven//:com.google.dagger.hilt.android.com.google.dagger.hilt.android.gradle.plugin_2.44.2",
        "@maven//:com.google.dagger.hilt-android-testing_2.44.2",
        "@maven//:com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin_1.8.10-1.0.9",
        "@maven//:com.google.protobuf.protobuf-bom_3.17.2",
        "@maven//:com.google.protobuf.protobuf-java_3.17.2",
        "@maven//:com.google.truth.truth_1.1.2",
        "@maven//:com.jakewharton.timber.timber_5.0.1",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.8.10",
        "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin_1.8.10",
        "@maven//:org.jetbrains.kotlin.kapt.org.jetbrains.kotlin.kapt.gradle.plugin_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-annotation-processing-gradle_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.9.20",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.9.20",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-jvm_1.6.4",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

iml_studio_test(
    name = "EditorPerformanceTest",
    timeout = "long",
    data = editor_performance_test_data,
    module = ":intellij.android.core.integration",
    tags = [
        "ci:perfgate-linux",
        "ci:perfgate-win",
        "noci:studio-linux",
        "noci:studio-win",
    ],
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.EditorPerformanceTest",
)

iml_studio_test(
    name = "LanguageHighlightingTest",
    timeout = "long",
    data = [
        ":languagehighlighting",
        ":languagehighlighting_deps",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.LanguageHighlightingTest",
)

iml_test(
    name = "EmulatorTest",
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.EmulatorTest",
)

iml_studio_test(
    name = "DebuggerTest",
    timeout = "long",
    data = [
        ":buildproject_deps",
        ":mindebugapp",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = [
        "block-network",
        "noci:studio-linux",
    ],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.DebuggerTest",
)

iml_test(
    name = "AdbServerConnectionTest",
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags = ["manual"],  # b/243923041
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.AdbServerConnectionTest",
)

iml_studio_test(
    name = "EmulatorBeforeStudioTest",
    timeout = "long",
    data = [
        ":minapp",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.EmulatorBeforeStudioTest",
)

iml_studio_test(
    name = "EmulatorAfterStudioTest",
    timeout = "long",
    data = [
        ":minapp",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/base/build-system:gradle-distrib-7.2",
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = [
        "block-network",
    ],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.EmulatorAfterStudioTest",
)

iml_studio_test(
    name = "GoToDeclarationTest",
    timeout = "long",
    data = [
        ":languagehighlighting",
        ":languagehighlighting_deps",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    test_class = "com.android.tools.idea.GoToDeclarationTest",
)

iml_studio_test(
    name = "LiveEditTest",
    timeout = "long",
    data = [
        ":live_edit_project_deps",
        ":liveeditproject",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "@system_image_android-33_default_x86_64//:x86_64-android-33-images",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = [
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.LiveEditTest",
)

apply_changes_data = [
    ":buildproject_deps",
    ":applychangesproject",
    "//prebuilts/studio/sdk:build-tools/33.0.1",
    "//prebuilts/studio/sdk:emulator",
    "//prebuilts/studio/sdk:platform-tools",
    "@system_image_android-33_default_x86_64//:x86_64-android-33-images",
    INTEGRATION_TEST_GRADLE_VERSION,
]

iml_studio_test(
    name = "ApplyChangesTest",
    timeout = "long",
    data = apply_changes_data,
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.ApplyChangesTest",
)

iml_studio_test(
    name = "ApplyCodeChangesTest",
    timeout = "long",
    data = apply_changes_data,
    module = ":intellij.android.core.integration",
    tags = [],
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.ApplyCodeChangesTest",
)

build_and_run_test_data = [
    ":buildproject_deps",
    ":minapp",
    "//prebuilts/studio/sdk:build-tools/33.0.1",
    "//prebuilts/studio/sdk:emulator",
    "//prebuilts/studio/sdk:platform-tools",
    INTEGRATION_TEST_GRADLE_VERSION,
    INTEGRATION_TEST_SYSTEM_IMAGE,
]

iml_studio_test(
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

create_project_test_data = [
    ":createproject_deps",
    "//prebuilts/studio/sdk:build-tools/30.0.3",
    "//prebuilts/studio/jdk/jdk17:jdk17_runtime_files",
    "//prebuilts/studio/sdk:platforms/latest",
    "//tools/base/build-system:gradle-distrib-7.5",  # The test itself sets a flag for which AGP version to use, so we can't rely on the framework choosing INTEGRATION_TEST_GRADLE_VERSION for us
]

iml_studio_test(
    name = "CreateProjectTest",
    timeout = "long",
    data = create_project_test_data,
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.CreateProjectTest",
)

maven_repository(
    name = "createproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.8.0",
        "@maven//:androidx.annotation.annotation-jvm_1.6.0",
        "@maven//:androidx.annotation.annotation_1.6.0",
        "@maven//:androidx.appcompat.appcompat_1.6.1",
        "@maven//:androidx.collection.collection-ktx_1.4.0",  # Required by CreateProjectTest after updating to Compose BOM 2024.04.01
        "@maven//:androidx.compose.compiler.compiler_1.5.1",
        "@maven//:androidx.compose.compose-bom_2024.04.01",
        "@maven//:androidx.compose.foundation.foundation-layout_1.5.0",
        "@maven//:androidx.compose.foundation.foundation_1.5.0",
        "@maven//:androidx.compose.material.material-icons-core_1.5.0",
        "@maven//:androidx.compose.material.material-ripple_1.5.0",
        "@maven//:androidx.compose.material.material_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.material3.material3_1.2.1",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-test-junit4_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-test-manifest_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.5.0",
        "@maven//:androidx.compose.ui.ui-tooling_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui_1.5.0",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.core.core-ktx_1.10.1",
        "@maven//:androidx.core.core-ktx_1.12.0",  # Required by CreateProjectTest after updating to Compose BOM 2024.04.01
        "@maven//:androidx.databinding.viewbinding_7.4.1",
        "@maven//:androidx.lifecycle.lifecycle-process_2.6.1",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.4.1",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.4.1",
        "@maven//:androidx.test.espresso.espresso-core_3.5.1",
        "@maven//:androidx.test.ext.junit_1.1.3",
        "@maven//:com.android.application.com.android.application.gradle.plugin_7.4.1",
        "@maven//:com.android.library.com.android.library.gradle.plugin_7.4.1",
        "@maven//:com.android.tools.build.aapt2_7.4.1-8841542",
        "@maven//:com.android.tools.build.gradle_7.4.1",
        "@maven//:com.google.android.material.material_1.5.0",
        "@maven//:com.google.protobuf.protobuf-bom_3.17.2",
        "@maven//:com.google.protobuf.protobuf-java_3.17.2",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_2.0.0",  # Empty Activity template Kotlin/Compose 2.0.0
        "@maven//:org.jetbrains.kotlin.kotlin-build-tools-impl_2.0.0",  # Empty Activity template Kotlin/Compose 2.0.0
        "@maven//:org.jetbrains.kotlin.kotlin-compose-compiler-plugin-embeddable_2.0.0",  # Empty Activity template Kotlin/Compose 2.0.0
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.6.21",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_2.0.0",  # Empty Activity template Kotlin/Compose 2.0.0
        "@maven//:org.jetbrains.kotlin.plugin.compose.org.jetbrains.kotlin.plugin.compose.gradle.plugin_2.0.0",  # Empty Activity template Kotlin/Compose 2.0.0
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.6.4",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-test_1.6.4",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
)

maven_repository(
    name = "openproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
)

iml_studio_test(
    name = "BuildProjectTest",
    timeout = "long",
    data = [
        ":buildproject_deps",
        ":minapp",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        INTEGRATION_TEST_GRADLE_VERSION,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.BuildProjectTest",
)

iml_studio_test(
    name = "BuildProjectBleakTest",
    timeout = "long",
    data = [
        ":buildproject_deps",
        ":minapp",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        INTEGRATION_TEST_GRADLE_VERSION,
        "//tools/adt/idea/bleak/src/com/android/tools/idea/bleak/agents:jnibleakhelper",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "ci:studio-linux_very_flaky",  # b/283129200
        "noci:studio-linux",
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.BuildProjectBleakTest",
)

iml_studio_test(
    name = "RunApkTest",
    timeout = "long",
    data = [
        ":helloworldapk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-31",
        "@system_image_android-33_default_x86_64//:x86_64-android-33-images",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "noci:studio-win",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    tags_linux = [
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
    ],
    test_class = "com.android.tools.idea.RunApkTest",
)

iml_studio_test(
    name = "DebugNativeApkTest",
    timeout = "long",
    data = [
        ":minnativeapp-apk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-31",
        "@system_image_android-33_default_x86_64//:x86_64-android-33-images",
    ],
    module = ":intellij.android.core.integration",
    tags_linux = [
        "block-network",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/260867011
    ],
    tags_windows = [
        # TODO(b/287284693): Add Windows support for this test.
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.DebugNativeApkTest",
)

iml_studio_test(
    name = "BuildAndRunInstrumentedTest",
    timeout = "long",
    data = [
        ":instrumenttestapp",
        ":run_instrumented_test_project_deps",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_GRADLE_VERSION,
        "@system_image_android-33_aosp_atd_x86_64//:x86_64-android-33AospAtd-images",
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

iml_studio_test(
    name = "MultipleDevicesInstrumentedTest",
    timeout = "long",
    data = [
        ":instrumenttestapp",
        ":run_instrumented_test_project_deps",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        INTEGRATION_TEST_GRADLE_VERSION,
        "@system_image_android-33_aosp_atd_x86_64//:x86_64-android-33AospAtd-images",
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.MultipleDevicesInstrumentedTest",
)

iml_studio_test(
    name = "DebugJUnitTest",
    timeout = "long",
    data = [
        ":debug_junit_test_deps",
        ":debugjunittest",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        INTEGRATION_TEST_GRADLE_VERSION,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
    ],
    test_class = "com.android.tools.idea.DebugJUnitTest",
)

iml_studio_test(
    name = "BuildAndRunKMPTest",
    timeout = "long",
    data = [
        ":buildkmpproject_deps",
        ":kmpapp",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-33",
        INTEGRATION_TEST_GRADLE_VERSION,
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.BuildAndRunKMPTest",
)

iml_studio_test(
    name = "SamplesImportTest",
    data = open_project_data,
    module = ":intellij.android.core.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.SamplesImportTest",
)

maven_repository(
    name = "buildproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:com.google.protobuf.protobuf-bom_3.17.2",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
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
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.5.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.3.1",
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
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
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:com.android.tools.emulator.proto_30.2.0",
        "@maven//:com.android.tools.utp.android-device-provider-ddmlib_31.1.0",
        "@maven//:com.android.tools.utp.android-device-provider-gradle_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-additional-test-output_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-apk-installer_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-coverage_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-device-info-proto_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-device-info_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-emulator-control_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-logcat_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-host-retention_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-result-listener-gradle-proto_31.1.0",
        "@maven//:com.android.tools.utp.android-test-plugin-result-listener-gradle_31.1.0",
        "@maven//:com.google.android.material.material_1.5.0",
        "@maven//:com.google.testing.platform.android-device-provider-local_0.0.8-alpha08",
        "@maven//:com.google.testing.platform.android-driver-instrumentation_0.0.8-alpha08",
        "@maven//:com.google.testing.platform.android-test-plugin_0.0.8-alpha08",
        "@maven//:com.google.testing.platform.core_0.0.8-alpha08",
        "@maven//:com.google.testing.platform.launcher_0.0.8-alpha08",
        "@maven//:javax.annotation.javax.annotation-api_1.3.2",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-bom_1.8.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler-embeddable_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler-embeddable_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.10",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-jvm_1.5.2",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.5.2",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

maven_repository(
    name = "showusages_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "languagehighlighting_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "debug_junit_test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.appcompat.appcompat_1.4.1",
        "@maven//:androidx.core.core-ktx_1.8.0",
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler-embeddable_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

maven_repository(
    name = "buildkmpproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.7.2",
        "@maven//:androidx.compose.compiler.compiler_1.4.7",
        "@maven//:androidx.compose.compose-bom_2023.06.00",
        "@maven//:androidx.compose.material.material_1.4.3",
        "@maven//:androidx.compose.runtime.runtime-saveable_1.4.3",
        "@maven//:androidx.compose.runtime.runtime_1.0.1",
        "@maven//:androidx.compose.runtime.runtime_1.4.3",
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.4.3",
        "@maven//:androidx.compose.ui.ui-tooling_1.4.3",
        "@maven//:androidx.compose.ui.ui_1.0.1",
        "@maven//:androidx.core.core-ktx_1.10.1",
        "@maven//:androidx.core.core-ktx_1.9.0",
        "@maven//:androidx.datastore.datastore-core-android_1.1.0-alpha04",
        "@maven//:androidx.datastore.datastore-core-jvm_1.1.0-alpha04",
        "@maven//:androidx.datastore.datastore-core-okio-jvm_1.1.0-alpha04",
        "@maven//:androidx.datastore.datastore-core-okio_1.1.0-alpha04",
        "@maven//:androidx.datastore.datastore-preferences-core-jvm_1.1.0-alpha04",
        "@maven//:androidx.datastore.datastore-preferences-core_1.1.0-alpha04",
        "@maven//:androidx.lifecycle.lifecycle-common_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-process_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime-compose_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-compose_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_2.6.1",
        "@maven//:com.android.application.com.android.application.gradle.plugin_8.1.0",
        "@maven//:com.android.library.com.android.library.gradle.plugin_8.1.0",
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.lint.lint-gradle_31.1.0",
        "@maven//:com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin_1.8.21-1.0.11",
        "@maven//:com.google.devtools.ksp.symbol-processing_1.8.21-1.0.11",
        "@maven//:com.google.protobuf.protobuf-bom_3.17.2",
        "@maven//:com.google.protobuf.protobuf-java_3.17.2",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.8.21",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.21",
        "@maven//:org.jetbrains.kotlin.kotlin-klib-commonizer-embeddable_1.8.21",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.6.21",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.9.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.21",
        "@maven//:org.jetbrains.kotlin.multiplatform.org.jetbrains.kotlin.multiplatform.gradle.plugin_1.8.21",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-android_1.7.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-bom_1.7.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-jvm_1.7.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.7.1",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "mindebugapp",
    srcs = glob(["testData/mindebugapp/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "liveeditproject",
    srcs = glob(["testData/liveedit/**"]),
)

filegroup(
    name = "applychangesproject",
    srcs = glob(["testData/applychanges/**"]),
)

filegroup(
    name = "helloworldapk",
    srcs = glob(["testData/helloworldapk/**"]),
)

filegroup(
    name = "minnativeapp-apk",
    srcs = glob(["testData/minnativeapp-apk/**"]),
)

filegroup(
    name = "instrumenttestapp",
    srcs = glob(["testData/InstrumentedTestApp/**"]),
)

filegroup(
    name = "debugjunittest",
    srcs = glob(["testData/JUnitTestApp/**"]),
)

filegroup(
    name = "languagehighlighting",
    srcs = glob(["testData/languagehighlighting/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "kmpapp",
    srcs = glob(["testData/kmpapp/**"]),
)

filegroup(
    name = "architectureSamples",
    srcs = glob(["testData/architecture-samples/**"]),
)

filegroup(
    name = "architectureSamplesScenarioScripts",
    srcs = glob(["testData/architecture-samples-scenarios/**"]),
)
