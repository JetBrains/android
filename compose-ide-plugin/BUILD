load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-ide-plugin",
    srcs = ["src"],
    iml_files = ["intellij.android.compose-ide-plugin.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/intellij.android.compose-common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/compose-ide-plugin:compose-compiler",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module]",
        "//tools/adt/idea/android-material:intellij.android.android-material[module]",
        "//tools/adt/idea/completion:intellij.android.completion[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-ide-plugin.tests",
    iml_files = ["intellij.android.compose-ide-plugin.tests.iml"],
    tags = ["no_test_windows"],  # b/208245297
    test_class = "com.android.tools.tests.ComposeTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:licenses",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/compose-ide-plugin/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    test_friends = ["//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:truth",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin[module, test]",
        "@intellij//:org.jetbrains.kotlin[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module, test]",
        "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api[module, test]",
        "//tools/adt/idea/intellij.android.compose-common[module, test]",
        "//tools/adt/idea/compose-ide-plugin:compose-compiler",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-material:intellij.android.android-material[module, test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "compose-compiler",
    jars = ["//tools/adt/idea/compose-ide-plugin/compose-compiler:compose-compiler.jar"],
    visibility = ["//visibility:public"],
)
