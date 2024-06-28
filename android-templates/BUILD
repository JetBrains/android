load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.templates",
    srcs = ["src"],
    iml_files = ["intellij.android.templates.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository[module]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.templates.tests",
    iml_files = ["intellij.android.templates.tests.iml"],
    split_test_targets = {
        "TemplateDiffTest": {
            "data": [
                "//tools/adt/idea/android/testData",
                "//prebuilts/studio/sdk:cmake",  # Needed for the C++ templates in TemplateDiffTest
                "//prebuilts/studio/sdk:ndk",  # Needed for the C++ templates in TemplateDiffTest
                "//tools/data-binding:data_binding_runtime.zip",  # Needed for some templates that use view binding in TemplateDiffTest
            ],
            "test_filter": "com.android.tools.idea.templates.diff.TemplateDiffTest",
        },
        "KotlinBomPlatformDependencyTest": {
            "test_filter": "com.android.tools.idea.templates.KotlinBomPlatformDependencyTest",
        },
        "other": {
            "data": [
                "//tools/adt/idea/android/testData",
            ],
        },
    },
    test_class = "com.android.tools.idea.templates.TemplateTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData:projects",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ] + glob(["testData/**"]),
    test_friends = ["//tools/adt/idea/android-templates:intellij.android.templates"],
    test_srcs = ["testSrc"],
    test_tags = [],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/observable:intellij.android.observable[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl[module, test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[module, test]",
        "//tools/adt/idea/android-templates:intellij.android.templates[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils[module, test]",
        "@intellij//:org.jetbrains.kotlin",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.7.2",  # Needed for TemplateDiffTest#testNewComposeWearActivity
        "@maven//:androidx.activity.activity-compose_1.8.0",
        "@maven//:androidx.activity.activity-ktx_1.6.0",  # Still needed from navigation-fragment-ktx:2.5.3
        "@maven//:androidx.activity.activity-ktx_1.7.0",
        "@maven//:androidx.activity.activity-ktx_1.8.0",
        "@maven//:androidx.annotation.annotation-jvm_1.6.0",
        "@maven//:androidx.annotation.annotation_1.6.0",
        "@maven//:androidx.appcompat.appcompat_1.6.1",
        "@maven//:androidx.collection.collection-ktx_1.4.0",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.animation.animation-core-android_1.5.0-beta01",  # Necessary for Compose for TV
        "@maven//:androidx.compose.animation.animation_1.3.0",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.animation.animation_1.5.0-beta01",  # Necessary for Compose for TV
        "@maven//:androidx.compose.animation.animation_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.compiler.compiler_1.5.1",
        "@maven//:androidx.compose.compose-bom_2022.10.00",
        "@maven//:androidx.compose.compose-bom_2024.04.01",
        "@maven//:androidx.compose.foundation.foundation-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.foundation.foundation-layout_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.foundation.foundation_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.foundation.foundation_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.material.material-icons-core_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.material.material-ripple_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.material.material_1.4.3",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.material.material_1.5.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.material.material_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.material3.material3_1.2.1",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.runtime.runtime-saveable_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.runtime.runtime_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-graphics-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-graphics_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-test-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-test-junit4-android_1.5.0-beta01",
        "@maven//:androidx.compose.ui.ui-test-junit4_1.3.0",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.ui.ui-test-junit4_1.5.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.ui.ui-test-junit4_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-test-junit4_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-test-manifest_1.5.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.ui.ui-test-manifest_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-test-manifest_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-tooling-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-data-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-preview-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-tooling_1.5.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.ui.ui-tooling_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-unit-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.concurrent.concurrent-futures-ktx_1.2.0-alpha02",
        "@maven//:androidx.concurrent.concurrent-futures_1.2.0-alpha02",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.4",
        "@maven//:androidx.core.core-ktx_1.10.0",  # Compose Activity
        "@maven//:androidx.core.core-ktx_1.10.1",
        "@maven//:androidx.core.core-ktx_1.11.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.core.core-ktx_1.11.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.core.core-ktx_1.12.0",
        "@maven//:androidx.core.core-splashscreen_1.0.1",
        "@maven//:androidx.core.core_1.12.0",
        "@maven//:androidx.core.core_1.5.0-beta01",
        "@maven//:androidx.core.core_1.5.0-rc02",
        "@maven//:androidx.emoji2.emoji2-views-helper_1.3.0",  # Compose BOM 2024.04.01; for testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.emoji2.emoji2-views-helper_1.4.0",  # Needed for TemplateDiffTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.emoji2.emoji2-views-helper_1.4.0-beta04",  # Necessary for Compose for TV.
        "@maven//:androidx.fragment.fragment-ktx_1.5.6",  # Needed for TemplateDiffTest#testNewViewModelFragmentWithKotlin (viewModelFragmentRecipe.kt)
        "@maven//:androidx.games.games-activity_1.2.2",
        "@maven//:androidx.leanback.leanback_1.0.0",
        "@maven//:androidx.legacy.legacy-support-v13_1.0.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.4.1",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-process_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.3.0-rc01",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-service_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-compose_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.4.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.3.0-rc01",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.6.1",
        "@maven//:androidx.media.media_1.6.0",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.6.0",
        "@maven//:androidx.navigation.navigation-fragment_2.6.0",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.6.0",
        "@maven//:androidx.palette.palette-ktx_1.0.0",
        "@maven//:androidx.preference.preference_1.2.0",
        "@maven//:androidx.recyclerview.recyclerview_1.3.0",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:androidx.slice.slice-builders_1.0.0",
        "@maven//:androidx.test.espresso.espresso-core_3.5.1",
        "@maven//:androidx.test.ext.junit_1.1.5",
        "@maven//:androidx.tv.tv-foundation_1.0.0-alpha07",
        "@maven//:androidx.tv.tv-material_1.0.0-alpha07",
        "@maven//:androidx.wear.compose.compose-material_1.2.1",
        "@maven//:androidx.wear.compose.compose-navigation_1.2.1",
        "@maven//:androidx.wear.compose.compose-ui-tooling_1.2.1",
        "@maven//:androidx.wear.tiles.tiles-material_1.2.0-alpha05",
        "@maven//:androidx.wear.tiles.tiles_1.2.0-alpha05",
        "@maven//:androidx.wear.watchface.watchface-complications-data-source-ktx_1.1.1",
        "@maven//:androidx.wear.wear-tooling-preview_1.0.0",
        "@maven//:androidx.wear.wear_1.2.0",
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.github.bumptech.glide.glide_4.11.0",
        "@maven//:com.google.ai.client.generativeai.generativeai_0.2.2",
        "@maven//:com.google.android.gms.play-services-ads_21.5.0",
        "@maven//:com.google.android.gms.play-services-base_17.6.0",
        "@maven//:com.google.android.gms.play-services-maps_18.1.0",
        "@maven//:com.google.android.gms.play-services-pay_16.1.0",
        "@maven//:com.google.android.gms.play-services-wallet_19.1.0",
        "@maven//:com.google.android.gms.play-services-wearable_18.0.0",
        "@maven//:com.google.android.horologist.horologist-compose-tools_0.4.8",
        "@maven//:com.google.android.horologist.horologist-tiles_0.4.8",
        "@maven//:com.google.android.libraries.mapsplatform.secrets-gradle-plugin.com.google.android.libraries.mapsplatform.secrets-gradle-plugin.gradle.plugin_2.0.1",  # Used by Maps templates
        "@maven//:com.google.android.material.material_1.10.0",
        "@maven//:com.google.android.material.material_1.5.0",
        "@maven//:com.google.android.material.material_1.9.0",
        "@maven//:com.google.android.support.wearable_2.9.0",
        "@maven//:com.google.android.wearable.wearable_2.9.0",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.google.prefab.cli_2.1.0",
        "@maven//:com.squareup.okio.okio-jvm_3.0.0",
        "@maven//:com.squareup.okio.okio_3.4.0",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:io.ktor.ktor-client-content-negotiation-jvm_2.3.2",
        "@maven//:io.ktor.ktor-client-content-negotiation_2.3.2",
        "@maven//:io.ktor.ktor-client-core-jvm_2.3.2",
        "@maven//:io.ktor.ktor-client-core_2.3.2",
        "@maven//:io.ktor.ktor-client-logging-jvm_2.3.2",
        "@maven//:io.ktor.ktor-client-logging_2.3.2",
        "@maven//:io.ktor.ktor-client-okhttp-jvm_2.3.2",
        "@maven//:io.ktor.ktor-client-okhttp_2.3.2",
        "@maven//:io.ktor.ktor-events_2.3.2",
        "@maven//:io.ktor.ktor-http_2.3.2",
        "@maven//:io.ktor.ktor-io_2.3.2",
        "@maven//:io.ktor.ktor-serialization-kotlinx-json-jvm_2.3.2",
        "@maven//:io.ktor.ktor-serialization-kotlinx-json_2.3.2",
        "@maven//:io.ktor.ktor-serialization-kotlinx_2.3.2",
        "@maven//:io.ktor.ktor-serialization_2.3.2",
        "@maven//:io.ktor.ktor-utils_2.3.2",
        "@maven//:io.ktor.ktor-websocket-serialization_2.3.2",
        "@maven//:io.ktor.ktor-websockets_2.3.2",
        "@maven//:javax.inject.javax.inject_1",
        "@maven//:junit.junit_4.12",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.hamcrest.hamcrest-library_1.3",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.7.20",  # Needed for KotlinBomPlatformDependencyTest
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.8.10",  # Needed for KotlinBomPlatformDependencyTest
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.9.0",  # Now needed for any Kotlin templates
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_2.0.0",  # Compose/Kotlin 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.kotlin-bom_1.8.0",
        "@maven//:org.jetbrains.kotlin.kotlin-bom_1.8.22",
        "@maven//:org.jetbrains.kotlin.kotlin-build-tools-impl_2.0.0",  # Compose/Kotlin 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.kotlin-compiler-embeddable_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler-embeddable_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-compose-compiler-plugin-embeddable_2.0.0",  # Compose/Kotlin 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.8.0",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_2.0.0",  # Compose/Kotlin 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.22",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_2.0.0",  # Compose/Kotlin 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.plugin.compose.org.jetbrains.kotlin.plugin.compose.gradle.plugin_2.0.0",  # Compose/Kotlin 2.0.0 templates
        "@maven//:org.jetbrains.kotlinx.atomicfu-jvm_0.17.0",
        "@maven//:org.jetbrains.kotlinx.atomicfu_0.17.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-android_1.6.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-android_1.7.3",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.6.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.7.1",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.7.3",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-jdk8_1.7.3",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-reactive_1.7.3",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-slf4j_1.7.3",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-test_1.7.1",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-test_1.7.3",
        "@maven//:org.jetbrains.kotlinx.kotlinx-serialization-bom_1.6.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-serialization-core-jvm_1.6.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-serialization-core_1.6.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-serialization-json-jvm_1.5.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-serialization-json_1.5.1",
        "@maven//:org.reactivestreams.reactive-streams_1.0.3",
        "@maven//:org.slf4j.slf4j-api_1.7.36",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)
