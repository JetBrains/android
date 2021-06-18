load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

# managed by go/iml_to_build
iml_module(
    name = "kotlin-integration-tests",
    iml_files = ["kotlin-integration-tests.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.KotlinIntegrationTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/kotlin-integration/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo",
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

maven_repo(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "//prebuilts/tools/common/m2:commons-logging.commons-logging.1.1.1",
        "//prebuilts/tools/common/m2:junit.junit.4.12",
        "//prebuilts/tools/common/m2:net.bytebuddy.byte-buddy.1.9.10",
        "//prebuilts/tools/common/m2:net.bytebuddy.byte-buddy-agent.1.9.10",
        "//prebuilts/tools/common/m2:org.hamcrest.hamcrest-core.1.3",
        "//prebuilts/tools/common/m2:org.hamcrest.hamcrest-integration.1.3",
        "//prebuilts/tools/common/m2:org.hamcrest.hamcrest-library.1.3",
        "//prebuilts/tools/common/m2:org.jdeferred.jdeferred-android-aar.1.2.3_aar",
        "//prebuilts/tools/common/m2:org.jdeferred.jdeferred-core.1.2.3",
        "//prebuilts/tools/common/m2:org.jetbrains.intellij.deps.trove4j.1.0.20181211",
        "//prebuilts/tools/common/m2:org.mockito.mockito-core.3.0.0",
        "//prebuilts/tools/common/m2:org.objenesis.objenesis.2.6",
        "//prebuilts/tools/common/m2:org.slf4j.slf4j-api.1.7.2",
    ],
)
