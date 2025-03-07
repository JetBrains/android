load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.sync",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.gradle.sync.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:JUnit",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api",
        "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-impl:intellij.android.gradle-tooling.impl",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/memory-usage",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.sync.tests",
    iml_files = ["intellij.android.projectSystem.gradle.sync.tests.iml"],
    test_class = "com.android.tools.idea.projectsystem.gradle.sync.GradleProjectSystemSyncTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:JUnit",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[test]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:junit.junit_4.12",
    ],
)
