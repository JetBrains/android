load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-designer",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["intellij.android.compose-designer.iml"],
    kotlin_use_compose = True,
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/compose-designer:ui-animation-tooling-internal",
        "//tools/adt/idea/intellij.android.compose-common",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/base/environment-services:studio.intellij.android.environment-services",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
        "//tools/adt/idea/preview-fast-compile:intellij.android.preview-fast-compile",
        "//tools/adt/idea/visual-lint:intellij.android.visual-lint",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-designer.tests",
    generate_k1_tests = True,
    iml_files = ["intellij.android.compose-designer.tests.iml"],
    kotlin_use_compose = True,
    split_test_targets = {
        "gradle.fast": {
            "tags": ["cpu:3"],
            "shard_count": 2,
            "test_filter": "com.android.tools.idea.compose.gradle.fast",
        },
        "gradle.navigation": {
            "test_filter": "com.android.tools.idea.compose.gradle.navigation",
        },
        "gradle.preview": {
            "tags": ["cpu:3"],
            "shard_count": 3,
            "test_filter": "com.android.tools.idea.compose.gradle.preview",
        },
        "gradle.runconfiguration": {
            "test_filter": "com.android.tools.idea.compose.gradle.preview.runconfiguration",
        },
        # Can probably be grouped into gradle-other once flakiness is fixed: b/279558854
        "AccessibilityModelUpdaterTest": {
            "test_filter": "com.android.tools.idea.compose.gradle.preview.AccessibilityModelUpdaterTest",
        },
        # Source of many flakes. Splitting it into an individual target for better investigation.
        "ComposePreviewRepresentationGradleTest": {
            "shard_count": 3,
            "test_filter": "com.android.tools.idea.compose.gradle.preview.ComposePreviewRepresentationGradleTest",
        },
        # Can probably be grouped into gradle-other once flakiness is fixed: b/261126651, b/272048512
        "RenderErrorTest": {
            "shard_count": 4,
            "test_filter": "com.android.tools.idea.compose.gradle.preview.RenderErrorTest",
        },
        "gradle-other": {
            "tags": ["cpu:3"],
            "shard_count": 8,
            "test_filter": "com.android.tools.idea.compose.gradle",
        },
        "animation": {
            "shard_count": 2,
            "test_filter": "com.android.tools.idea.compose.preview.animation",
        },
        "runconfiguration": {
            "test_filter": "com.android.tools.idea.compose.preview.runconfiguration",
        },
        "fast": {
            "test_filter": "com.android.tools.idea.compose.preview.fast",
        },
        # Source of many flakes. Splitting it into an individual target for better investigation.
        "ComposePreviewRepresentationTest": {
            "test_filter": "com.android.tools.idea.compose.preview.ComposePreviewRepresentationTest",
        },
        "other": {},
    },
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.compose.preview.ComposePreviewTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:build-tools/35.0.0",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/android-35",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/compose-designer/testData:classloader",
        "//tools/adt/idea/compose-designer/testData:test_projects",
        "//tools/adt/idea/compose-ide-plugin/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_for_compose_prebuilts",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/deploy:intellij.android.deploy",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/compose-designer:ui-animation-tooling-internal",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer.tests",
        "//tools/adt/idea/preview-fast-compile:intellij.android.preview-fast-compile",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/visual-lint:intellij.android.visual-lint",
        "//tools/adt/idea/backup:intellij.android.backup.api",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
    ],
    test_friends = ["//tools/adt/idea/compose-designer:intellij.android.compose-designer"],
    test_jvm_flags = ["-Dcom.google.testing.junit.runner.shouldInstallTestSecurityManager=false"],
    test_resources = ["testData"],
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
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/intellij.android.compose-common",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "ui-animation-tooling-internal",
    jars = ["lib/ui-animation-tooling-internal.jar"],
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.9.3",
        "@maven//:androidx.annotation.annotation_1.2.0-rc01",
        "@maven//:androidx.appcompat.appcompat_1.7.0",
        "@maven//:androidx.collection.collection-ktx_1.5.0-alpha06",
        "@maven//:androidx.collection.collection_1.5.0-alpha06",
        "@maven//:androidx.compose.animation.animation_1.8.0-alpha06",
        "@maven//:androidx.compose.foundation.foundation_1.8.0-alpha06",
        "@maven//:androidx.compose.material.material_1.8.0-alpha06",
        "@maven//:androidx.compose.ui.ui-tooling_1.8.0-alpha06",
        "@maven//:androidx.core.core-ktx_1.13.1",
        "@maven//:androidx.emoji2.emoji2-views-helper_1.4.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.5.1",
        "@maven//:androidx.lifecycle.lifecycle-common_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-livedata_2.8.7",
        "@maven//:androidx.lifecycle.lifecycle-process_2.8.7",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.8.7",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_2.8.7",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:javax.inject.javax.inject_1",
        "@maven//:junit.junit_4.12",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.hamcrest.hamcrest-library_1.3",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.9.23",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.9.23",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.9.23",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.9.23",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.9.23",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-designer.integration",
    enable_tests = False,
    iml_files = ["intellij.android.compose-designer.integration.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
    ],
    test_srcs = ["integrationTestSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:JUnit",
    ],
)

compose_preview_data = [
    ":compose_preview_deps",
    "//prebuilts/studio/sdk:build-tools/33.0.1",
    "//tools/adt/idea/compose-designer/testData:composepreview",
    INTEGRATION_TEST_GRADLE_VERSION,
]

maven_repository(
    name = "compose_preview_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.5.1",
        "@maven//:androidx.compose.compiler.compiler_1.3.2",
        "@maven//:androidx.compose.material.material_1.3.0",
        "@maven//:androidx.compose.ui.ui-test-manifest_1.3.0",
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.3.0",
        "@maven//:androidx.compose.ui.ui-tooling_1.3.0",
        "@maven//:androidx.compose.ui.ui_1.3.0",
        "@maven//:androidx.core.core-ktx_1.9.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.3.1",
        "@maven//:com.android.tools.build.aapt2_8.1.0-10154469",
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-parcelize-compiler_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-parcelize-runtime_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

iml_test(
    name = "ComposePreviewKotlinTest",
    timeout = "long",
    data = compose_preview_data,
    module = ":intellij.android.compose-designer.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "noci:studio-win",  # TODO(b/383596974): flaky timeouts on Windows
    ],
    test_class = "com.android.tools.idea.compose.ComposePreviewKotlin",
)
