"""
This module contains common constants used in builds/tests.
"""

# The version of Gradle to use for integration tests. This must be kept
# in-sync with code (search the codebase for
# "INTEGRATION_TEST_GRADLE_VERSION").
INTEGRATION_TEST_GRADLE_VERSION = "//tools/base/build-system:gradle-distrib-8.6"

# The emulator to use for integration tests. This must be kept in-sync with
# code (search the codebase for "INTEGRATION_TEST_SYSTEM_IMAGE").
INTEGRATION_TEST_SYSTEM_IMAGE = "@system_image_android-31_default_x86_64//:x86_64-android-31-images"

# Kotlin version used in test projects
KOTLIN_VERSION_FOR_TESTS = "1.9.22"

# Kotlin artifacts required for building test projects
# NOTE: These artifacts should have the same version. If you need more versions, create a separate list (e.g., KOTLIN_1_9_0_ARTIFACTS).
KOTLIN_ARTIFACTS_FOR_TESTS = [
    "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kapt.org.jetbrains.kotlin.kapt.gradle.plugin_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-annotation-processing-gradle_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-compiler_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-reflect_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_" + KOTLIN_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_" + KOTLIN_VERSION_FOR_TESTS,
]
