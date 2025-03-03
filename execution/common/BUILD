load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.execution.common",
    srcs = ["src"],
    iml_files = ["intellij.android.execution.common.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/deploy:intellij.android.deploy",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/build-common:intellij.android.buildCommon",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/analytics",
        "//tools/base/tracer:studio.android.sdktools.tracer",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/base/backup:studio.android.sdktools.backup",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/adt/idea/backup:intellij.android.backup.api",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.execution.common.tests",
    iml_files = ["intellij.android.execution.common.tests.iml"],
    split_test_targets = {
        "applychanges": {
            "test_filter": "com.android.tools.idea.execution.common.applychanges",
            "tags": ["noci:studio-win"],
        },
        "AndroidJavaDebuggerTest": {
            "test_filter": "AndroidJavaDebuggerTest",
        },
        "other": {},
    },
    test_class = "com.android.tools.idea.execution.common.AndroidExecutionCommonTestSuite",
    # keep sorted
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
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[test]",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[test]",
        "//tools/base/manifest-parser:studio.android.sdktools.manifest-parser[test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.android.support.test.runner_1.0.2",
        "@maven//:com.google.guava.guava_19.0",
    ],
)
