"""
This module contains common constants used in builds/tests.
"""

# The version of Gradle to use for integration tests. This must be kept
# in-sync with code (search the codebase for
# "INTEGRATION_TEST_GRADLE_VERSION").
INTEGRATION_TEST_GRADLE_VERSION = "//tools/base/build-system:gradle-distrib-9.1.0"

# The emulator to use for integration tests. This must be kept in-sync with
# code (search the codebase for "INTEGRATION_TEST_SYSTEM_IMAGE").
INTEGRATION_TEST_SYSTEM_IMAGE = "//prebuilts/studio/sdk:system_image_android-31_default"

# The build tools to use for integration tests.
INTEGRATION_TEST_BUILD_TOOLS = "//prebuilts/studio/sdk:build-tools/36.0.0"

# Kotlin version used in test projects
KOTLIN_VERSION_FOR_TESTS = "2.2.10"

# Kotlin artifacts required for building test projects
# NOTE: These artifacts should have the same version. If you need more versions, create a separate list (e.g., KOTLIN_1_9_0_ARTIFACTS).
KOTLIN_ARTIFACTS_FOR_TESTS = [
    "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-annotation-processing-gradle_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-build-tools-impl_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-compiler_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-compiler-embeddable_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-compose-compiler-plugin-embeddable_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-daemon-embeddable_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-reflect_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-test_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.plugin.compose.org.jetbrains.kotlin.plugin.compose.gradle.plugin_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.plugin.serialization.org.jetbrains.kotlin.plugin.serialization.gradle.plugin_" + KOTLIN_VERSION_FOR_TESTS,
]

# AGP artifacts required for building test projects
AGP_ARTIFACTS_FOR_TESTS = [
    "@maven//:com.android.application.com.android.application.gradle.plugin_9.0.0",
    "@maven//:com.android.library.com.android.library.gradle.plugin_9.0.0",
    "@maven//:com.android.tools.build.aapt2_9.0.0-14304508",
    "@maven//:com.android.tools.build.gradle_9.0.0",
]
