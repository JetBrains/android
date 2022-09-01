load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/adt/idea/kotlin-integration:version.bzl", "KOTLIN_IDE_VERSION")

# The maven artifact aliases below exist in order to simplify Kotlin plugin updates.
# If a test requires Kotlin maven artifacts with a version matching the Kotlin
# IDE plugin, then the test should use these aliases.

alias(
    name = "kotlin-android-extensions-runtime-for-ide-plugin",
    actual = "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_" + KOTLIN_IDE_VERSION,
    visibility = ["//visibility:public"],
)

alias(
    name = "kotlin-gradle-plugin-for-ide-plugin",
    actual = "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_" + KOTLIN_IDE_VERSION,
    visibility = ["//visibility:public"],
)

alias(
    name = "kotlin-reflect-for-ide-plugin",
    actual = "@maven//:org.jetbrains.kotlin.kotlin-reflect_" + KOTLIN_IDE_VERSION,
    visibility = ["//visibility:public"],
)

alias(
    name = "kotlin-script-runtime-for-ide-plugin",
    actual = "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_" + KOTLIN_IDE_VERSION,
    visibility = ["//visibility:public"],
)

alias(
    name = "kotlin-stdlib-jdk8-for-ide-plugin",
    actual = "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_" + KOTLIN_IDE_VERSION,
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "kotlin-integration-tests",
    iml_files = ["kotlin-integration-tests.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.KotlinIntegrationTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/kotlin-integration/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    test_shard_count = 2,
    test_srcs = ["testSrc"],
    test_tags = [
        "no_test_mac",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.output.parser[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:commons-logging.commons-logging_1.1.1",
        "@maven//:junit.junit_4.12",
        "@maven//:org.hamcrest.hamcrest-integration_1.3",
        "@maven//:org.jdeferred.jdeferred-android-aar_1.2.3",
        "@maven//:org.jetbrains.intellij.deps.trove4j_1.0.20181211",
        "@maven//:org.mockito.mockito-core_3.12.4",
    ],
)
