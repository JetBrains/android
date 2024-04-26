load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "KOTLIN_VERSION_FOR_TESTS")
load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer",
    srcs = ["src"],
    iml_files = ["intellij.android.designer.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    kotlin_use_compose = True,
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    test_srcs = ["testFramework"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:com.intellij.platform.images",
        "//tools/adt/idea/assistant[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module]",
        "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl[module]",
        "//tools/adt/idea/.idea/libraries:layoutlib",
        "//tools/adt/idea/lint:intellij.lint[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/adt/idea/intellij.android.compose-common[module]",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module]",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module]",
        "//tools/adt/idea/ml-api:intellij.android.ml-api[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer.tests",
    iml_files = ["intellij.android.designer.tests.iml"],
    kotlin_use_compose = True,
    split_test_targets = {
        "common": {
            "test_filter": "com.android.tools.idea.common",
        },
        "motion": {
            "test_filter": "com.android.tools.idea.uibuilder.motion",
        },
        "visual": {
            "test_filter": "com.android.tools.idea.uibuilder.visual",
        },
        "designer": {
            "shard_count": 4,
        },
    },
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.DesignerTestSuite",
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
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
    ],
    test_friends = ["//tools/adt/idea/designer:intellij.android.designer"],
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
        "cpu:3",
        "no_test_mac",  # b/72973198
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/assistant[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module]",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.output.parser[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module, test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module, test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module, test]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/ml-api:intellij.android.ml-api[module, test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
    ],
)

maven_repository(
    name = "test_deps",
    artifacts = [
        "@maven//:androidx.appcompat.appcompat_1.6.1",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.core.core-ktx_1.9.0",
        "@maven//:com.android.support.constraint.constraint-layout_2.0.4",
        "@maven//:com.android.support.design_28.0.0",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.google.android.material.material_1.8.0",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_" + KOTLIN_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_" + KOTLIN_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.6.1",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer.integration",
    enable_tests = False,
    iml_files = ["intellij.android.designer.integration.iml"],
    test_srcs = ["integrationTestSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "@intellij//:JUnit",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
    ],
)

layout_editor_data = [
    ":layout_preview_deps",
    "//prebuilts/studio/sdk:build-tools/33.0.1",
    "//prebuilts/studio/sdk:emulator",
    "//prebuilts/studio/sdk:platform-tools",
    "//tools/adt/idea/designer/testData:simpleApplication",
    "//tools/adt/idea/designer/testData:visualLintApplication",
    INTEGRATION_TEST_GRADLE_VERSION,
]

maven_repository(
    name = "layout_preview_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:com.google.android.material.material_1.5.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_" + KOTLIN_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_" + KOTLIN_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_" + KOTLIN_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

iml_studio_test(
    name = "LayoutEditorPreviewTest",
    timeout = "long",
    data = layout_editor_data,
    module = ":intellij.android.designer.integration",
    tags_linux = [
        "block-network",
        "cpu:100",
    ],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.designer.LayoutEditorPreviewTest",
)

iml_studio_test(
    name = "VisualLintTest",
    timeout = "long",
    data = layout_editor_data,
    module = ":intellij.android.designer.integration",
    tags_linux = ["block-network"],
    tags_windows = [
        "block-network",
        "manual",  # http://b/317124114
    ],
    test_class = "com.android.tools.idea.designer.VisualLintTest",
)
