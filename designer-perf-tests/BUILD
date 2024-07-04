load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer-perf-tests",
    iml_files = ["intellij.android.designer-perf-tests.iml"],
    split_test_targets = {
        "perfgateCompose": {
            "tags": ["perfgate"],
            "shard_count": 5,
            "test_filter": "com.android.tools.idea.rendering.PerfgateComposeTest",
        },
        "perfgateComplex": {
            "tags": ["perfgate"],
            "shard_count": 2,
            "test_filter": "com.android.tools.idea.rendering.PerfgateComplexRenderTest",
        },
        "perfgateVisualLint": {
            "tags": ["perfgate"],
            "shard_count": 5,
            "test_filter": "com.android.tools.idea.uibuilder.visual.visuallint",
        },
        "perfgateJarManager": {
            "tags": ["perfgate"],
            "test_filter": "com.android.tools.idea.rendering.PerfgateJarManagerTest",
        },
        "perfgateComposeStandardGradle": {
            "tags": ["perfgate"],
            "shard_count": 2,
            "test_filter": "com.android.tools.idea.rendering.gradle.PerfgateComposeStandardGradleTest",
        },
        "perfgateComposeEssentialsGradle": {
            "tags": ["perfgate"],
            "shard_count": 3,
            "test_filter": "com.android.tools.idea.rendering.gradle.PerfgateComposeEssentialsGradleTest",
        },
        "perfgateComposeRenderQualityGradle": {
            "tags": ["perfgate"],
            "shard_count": 3,
            "test_filter": "com.android.tools.idea.rendering.gradle.PerfgateComposeRenderQualityGradleTest",
        },
        "perfgateUiCheck": {
            "tags": ["perfgate"],
            "shard_count": 5,
            "test_filter": "com.android.tools.idea.uicheck",
        },
        "non-perfgate": {
        },
    },
    tags = [
        "no_mac",
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.rendering.PerfgateRenderTestSuite",
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
        "//tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native:libjni_object_tagger.prebuilt",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer-perf-tests/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
    ],
    test_jvm_flags = [
        "-Djdk.attach.allowAttachSelf=true",
    ],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
        "//tools/adt/idea/intellij.android.compose-common[module]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/preview-designer:intellij.android.preview-designer[module, test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module, test]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests[module, test]",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements[module, test]",
        "//tools/adt/idea/preview-fast-compile:intellij.android.preview-fast-compile[module, test]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.annotation.annotation-jvm_1.6.0",
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.collection.collection-ktx_1.4.0",  # Required by ComposeRenderTest after updating to Compose BOM 2024.04.01
        "@maven//:androidx.collection.collection_1.4.0",  # Required by ComposeRenderTest after updating to Compose BOM 2024.04.01
        "@maven//:androidx.compose.animation.animation_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.compiler.compiler_1.5.1",
        "@maven//:androidx.compose.foundation.foundation_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-tooling_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.core.core-ktx_1.10.0",
        "@maven//:androidx.core.core-ktx_1.12.0",  # Required by ComposeRenderTest after updating to Compose BOM 2024.04.01
        "@maven//:androidx.core.core_1.5.0-beta01",
        "@maven//:androidx.core.core_1.5.0-rc02",
        "@maven//:androidx.emoji2.emoji2-views-helper_1.4.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-common_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-livedata_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-process_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-service_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.6.1",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:com.android.support.constraint.constraint-layout_2.0.4",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.google.android.material.material_1.4.0",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:javax.inject.javax.inject_1",
        "@maven//:junit.junit_4.12",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.hamcrest.hamcrest-library_1.3",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.9.0",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)
