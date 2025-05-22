load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/base/build-system/integration-test:common-dependencies.bzl", "KGP_VERSION_FOR_TESTS")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.newProjectWizard",
    srcs = ["src"],
    iml_files = ["intellij.android.newProjectWizard.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/observable:intellij.android.observable",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui",
        "//tools/adt/idea/wizard:intellij.android.wizard",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-material:intellij.android.android-material",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/lint:intellij.lint",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe",
        "//tools/adt/idea/android-templates:intellij.android.templates",
        "//tools/adt/idea/analytics",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.newProjectWizard.tests",
    iml_files = ["intellij.android.newProjectWizard.tests.iml"],
    split_test_targets = {
        "npw.assetstudio": {
            "test_filter": "com.android.tools.idea.npw.assetstudio.ui",
        },
        "GroovyAddNewModulesToAppTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "com.android.tools.idea.npw.module.GroovyAddNewModulesToAppTest",
        },
        "GroovyVersionCatalogAddNewModulesToAppTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "com.android.tools.idea.npw.module.GroovyVersionCatalogAddNewModulesToAppTest",
        },
        "KtsAddNewModulesToAppTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "com.android.tools.idea.npw.module.KtsAddNewModulesToAppTest",
            # b/399316550 flaky on windows, gradle daemon not found?
            "tags": ["noci:studio-win"],
        },
        "KtsVersionCatalogAddNewModulesToAppTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "com.android.tools.idea.npw.module.KtsVersionCatalogAddNewModulesToAppTest",
            # b/399316550 flaky on windows, gradle daemon not found?
            "tags": ["noci:studio-win"],
        },
        "BenchmarkModuleTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "com.android.tools.idea.npw.module.BenchmarkModuleTest",
            # b/192269906 flaky on windows, taking longer than 15 minutes
            "tags": ["noci:studio-win"],
        },
        "AddBaselineProfilesModuleTest": {
            "test_filter": "com.android.tools.idea.npw.module.AddBaselineProfilesModuleTest",
        },
        "AddBaselineProfilesModuleKtTest": {
            "test_filter": "com.android.tools.idea.npw.module.AddBaselineProfilesModuleKtTest",
        },
        "NewModuleCompileSdkSelectionTest": {
            "test_filter": "com.android.tools.idea.npw.module.NewModuleCompileSdkSelectionTest",
            "data": [
                "//prebuilts/studio/sdk:platforms/android-33",
            ],
        },
        "ConfigureAndroidModuleStepTest": {
            "test_filter": "com.android.tools.idea.npw.module.ConfigureAndroidModuleStepTest",
            "data": [
                "//prebuilts/studio/sdk:platforms/android-31",
                "//prebuilts/studio/sdk:platforms/android-32",
                "//prebuilts/studio/sdk:platforms/android-33",
            ],
        },
        "ModuleModelTest": {
            "test_filter": "com.android.tools.idea.npw.module.ModuleModelTest",
        },
        "other": {
            "shard_count": 10,
        },
    },
    test_class = "com.android.tools.idea.npw.NewProjectWizardTestSuite",
    # keep sorted
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/jdk/jdk17:jdk_runtime_files",
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-35",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:gradle-distrib-8.0",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    test_tags = [],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin",
        "//tools/adt/idea/gradle-dsl-toml:intellij.android.gradle.dsl.toml",
    ],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[test]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/observable:intellij.android.observable[test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[test]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/lint:intellij.lint[test]",
        "//tools/base/common:studio.android.sdktools.common[test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[test]",
        "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl[test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[test]",
        "//tools/adt/idea/android-material:intellij.android.android-material[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[test]",
        "//tools/adt/idea/android-templates:intellij.android.templates[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-ktx_1.8.0",
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.benchmark.benchmark-baseline-profile-gradle-plugin_1.2.4",
        "@maven//:androidx.benchmark.benchmark-gradle-plugin_1.2.4",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.4",
        "@maven//:androidx.core.core-ktx_1.6.0",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.3.1",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.3.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.6.1",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.3.5",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.3.5",
        "@maven//:androidx.profileinstaller.profileinstaller_1.3.1",
        "@maven//:androidx.test.espresso.espresso-core_3.1.0",
        "@maven//:androidx.test.espresso.espresso-core_3.2.0",
        "@maven//:androidx.test.ext.junit_1.1.3-alpha02",
        "@maven//:androidx.test.ext.junit_1.1.5",
        "@maven//:androidx.test.runner_1.5.2",
        "@maven//:androidx.wear.watchface.watchface-guava_1.1.0",
        "@maven//:com.android.support.appcompat-v7_25.4.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.constraint.constraint-layout_2.0.4",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:com.google.android.material.material_1.4.0",
        "@maven//:com.google.guava.guava_19.0",
        "@maven//:org.jetbrains.kotlin.kotlin-build-tools-impl_2.0.21",  # AddNewModulesToAppTest
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.9.24",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_2.0.21",  # AddNewModulesToAppTest
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_2.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-native-prebuilt_" + KGP_VERSION_FOR_TESTS,  # Used by AddKotlinMultiplatformLibraryModuleTest
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.9.24",
        "@maven//:org.jetbrains.kotlin.kotlin-test-junit_" + KGP_VERSION_FOR_TESTS,  # Used by AddKotlinMultiplatformLibraryModuleTest
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.6.1",
    ],
)
