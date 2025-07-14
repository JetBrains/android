load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.gradle",
    iml_files = ["intellij.android.core.gradle.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/jdk/jdk17:jdk_runtime_files",
        "//prebuilts/studio/jdk/jdk8:jdk_runtime_files",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android:test_deps",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",  # Please do not add old versions of AGP here. Use the old-agp-tests module instead.
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/project-system-gradle-repository-search:intellij.android.projectSystem.gradle.repositorySearch",
        "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "//tools/adt/idea/android/gradle:libandroid-core-proto",
        "//tools/adt/idea/.idea/libraries:agp-version",
        "@intellij//:JUnit",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/gmaven:intellij.android.gmaven",
    ],
    test_jvm_flags = [
        "-Djdk.attach.allowAttachSelf=true",
    ],
    test_shard_count = 6,
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "libandroid-core-proto",
    jars = ["//tools/adt/idea/android:libandroid-core-proto.jar"],
    visibility = ["//visibility:public"],
)
