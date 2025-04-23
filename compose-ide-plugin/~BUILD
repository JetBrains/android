load("//tools/base/bazel:bazel.bzl", "iml_module")

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
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/intellij.android.compose-common",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/android-material:intellij.android.android-material",
        "//tools/adt/idea/completion:intellij.android.completion",
        "@intellij//:intellij.json.split",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-ide-plugin.tests",
    generate_k1_tests = True,
    iml_files = ["intellij.android.compose-ide-plugin.tests.iml"],
    tags = ["noci:studio-win"],  # b/208245297
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
        "@maven//:androidx.compose.runtime.runtime-android_1.7.1",
        "@maven//:androidx.compose.runtime.runtime-saveable-android_1.7.1",
        "@maven//:androidx.compose.ui.ui-android_1.7.1",
        "@maven//:androidx.compose.ui.ui-graphics-android_1.7.1",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-plugin:intellij.android.plugin",
        "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api",
        "//tools/adt/idea/intellij.android.compose-common",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/android-material:intellij.android.android-material",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "@intellij//:intellij.json.split",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
    ],
    test_friends = ["//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:truth",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "@intellij//:com.intellij.java",
    ],
)
