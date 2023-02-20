load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.newProjectWizard",
    srcs = ["src"],
    iml_files = ["intellij.android.newProjectWizard.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/lint:intellij.lint[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe[module]",
        "//tools/adt/idea/android-templates:intellij.android.templates[module]",
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
        "AddNewModulesToAppTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "com.android.tools.idea.npw.module.AddNewModulesToAppTest",
            # b/192102820 flaky on windows, taking longer than 15 minutes
            "tags": ["no_windows"],
        },
        "BenchmarkModuleTest": {
            # TODO(b/191881927) Test cases with a gradle KTS sync do not shard well
            "test_filter": "com.android.tools.idea.npw.module.BenchmarkModuleTest",
            # b/192269906 flaky on windows, taking longer than 15 minutes
            "tags": ["no_windows"],
        },
        "other": {},
    },
    test_class = "com.android.tools.idea.npw.NewProjectWizardTestSuite",
    # keep sorted
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    test_srcs = [
        "testResources",
        "testSrc",
    ],
    test_tags = [],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/observable:intellij.android.observable[module, test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module, test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.benchmark.benchmark-gradle-plugin_1.1.1",
        "@maven//:androidx.constraintlayout.constraintlayout_1.1.3",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.core.core-ktx_1.2.0",
        "@maven//:androidx.core.core-ktx_1.6.0",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.3.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.3.0-rc01",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.3.1",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.3.5",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.3.5",
        "@maven//:androidx.test.espresso.espresso-core_3.1.0",
        "@maven//:androidx.test.espresso.espresso-core_3.2.0",
        "@maven//:androidx.test.ext.junit_1.1.2",
        "@maven//:androidx.test.ext.junit_1.1.3-alpha02",
        "@maven//:com.android.support.appcompat-v7_25.4.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.constraint.constraint-layout_2.0.4",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.google.android.material.material_1.4.0",
        "@maven//:com.google.guava.guava_19.0",
        "@maven//:junit.junit_4.13.2",
    ],
)
