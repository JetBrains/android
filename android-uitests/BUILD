load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

filegroup(
    name = "aspect_rules",
    srcs = ["default_aspect_rules.json"],
)

filegroup(
    name = "aspects_baseline",
    srcs = ["aspects_baseline.txt"],
)

COMMON_DATA = [
    ":test_deps",
    "//tools/adt/idea/android-uitests:aspects_baseline",
    "//tools/adt/idea/android-uitests:aspect_rules",
    "//tools/adt/idea/bleak/src/com/android/tools/idea/bleak/agents:jnibleakhelper",
    "//tools/base/aspects_agent:aspects_agent",
    "//prebuilts/studio/sdk:build-tools/latest",
    "//prebuilts/studio/sdk:cmake",
    "//prebuilts/studio/sdk:ndk",
    "//prebuilts/studio/sdk:platforms/latest",
    "//prebuilts/studio/sdk:platform-tools",
    "//prebuilts/tools/common/aspects-agent:aspects_agent",
    "//prebuilts/tools:bazel-dist",
    "//tools/adt/idea/android/annotations",
    "//tools/adt/idea/artwork:device-art-resources",
    "//tools/base/build-system:android_gradle_plugin.zip",
    "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
    "//tools/base/build-system:gradle-distrib",
    "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    "//tools/base/build-system/integration-test:kotlin_gradle_plugin_for_compose_prebuilts",
    "//tools/data-binding:data_binding_runtime.zip",
    "//tools/vendor/google/testing/display:xvfb",
]

COMMON_TAGS = [
    "block-network",
    "cpu:3",
]

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.guiTests",
    iml_files = ["intellij.android.guiTests.iml"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:com.intellij.gradle[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:emulator-proto[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module, test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/swing-testing/fest-swing[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/uitest-framework:intellij.android.guiTestFramework[module, test]",
        "//tools/adt/idea/bleak:intellij.android.bleak[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor[module, test]",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/adt/idea/.idea/libraries:transport-proto[test]",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd[module, test]",
        "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl[module, test]",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module, test]",
        "//tools/adt/idea/studio[module, test]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api[module, test]",
        "@intellij//:org.jetbrains.kotlin[test]",
        "//tools/adt/idea/intellij.android.compose-common[module, test]",
        "//tools/adt/idea/device-explorer-files:intellij.android.device-explorer-files[module, test]",
        "//tools/adt/idea/android-navigator:intellij.android.navigator[module, test]",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module, test]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module, test]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module, test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module, test]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module, test]",
        "//tools/adt/idea/bleak-config:intellij.android.bleak-config[module, test]",
        "//tools/adt/idea/.idea/libraries:agp-version[test]",
    ],
)

# TODO (b/191383051): Trim artifacts down to what is actually used.
maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:android.arch.navigation.navigation-fragment_1.0.0",
        "@maven//:android.arch.navigation.navigation-ui_1.0.0",
        "@maven//:android.arch.persistence.room.runtime_1.0.0",
        "@maven//:androidx.activity.activity-compose_1.7.2",
        "@maven//:androidx.activity.activity-compose_1.8.0",
        "@maven//:androidx.activity.activity-ktx_1.6.0",
        "@maven//:androidx.activity.activity-ktx_1.7.0",
        "@maven//:androidx.activity.activity-ktx_1.8.0",
        "@maven//:androidx.annotation.annotation-jvm_1.6.0",
        "@maven//:androidx.annotation.annotation_1.6.0",
        "@maven//:androidx.appcompat.appcompat_1.0.2",
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.appcompat.appcompat_1.4.1",
        "@maven//:androidx.appcompat.appcompat_1.6.1",
        "@maven//:androidx.benchmark.benchmark-gradle-plugin_1.2.4",
        "@maven//:androidx.collection.collection-ktx_1.4.0",
        "@maven//:androidx.collection.collection_1.4.0",
        "@maven//:androidx.compose.animation.animation-core-android_1.5.0-beta01",  # Necessary for Compose for TV
        "@maven//:androidx.compose.animation.animation-core-android_1.6.6",  # Necessary for Compose for TV
        "@maven//:androidx.compose.animation.animation_1.3.0",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.animation.animation_1.5.0-beta01",  # Necessary for Compose for TV
        "@maven//:androidx.compose.animation.animation_1.6.0",  # Necessary for Compose for TV
        "@maven//:androidx.compose.animation.animation_1.6.6",  # Necessary for Compose for TV
        "@maven//:androidx.compose.compiler.compiler_1.3.2",
        "@maven//:androidx.compose.compiler.compiler_1.5.1",
        "@maven//:androidx.compose.compose-bom_2022.10.00",
        "@maven//:androidx.compose.compose-bom_2024.04.01",
        "@maven//:androidx.compose.foundation.foundation-android_1.5.0-beta01",  # Necessary for Compose for TV
        "@maven//:androidx.compose.foundation.foundation-android_1.6.0",  # Necessary for Compose for TV
        "@maven//:androidx.compose.foundation.foundation-android_1.6.6",  # Necessary for Compose for TV
        "@maven//:androidx.compose.foundation.foundation-layout_1.3.0",
        "@maven//:androidx.compose.foundation.foundation-layout_1.5.0",
        "@maven//:androidx.compose.foundation.foundation-layout_1.6.6",
        "@maven//:androidx.compose.foundation.foundation_1.3.0",
        "@maven//:androidx.compose.foundation.foundation_1.5.0",
        "@maven//:androidx.compose.foundation.foundation_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.foundation.foundation_1.6.0",
        "@maven//:androidx.compose.foundation.foundation_1.6.6",
        "@maven//:androidx.compose.material.material-icons-core_1.6.6",
        "@maven//:androidx.compose.material.material-ripple_1.6.6",
        "@maven//:androidx.compose.material.material_1.3.0",
        "@maven//:androidx.compose.material.material_1.4.3",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.material.material_1.5.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.material.material_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.material3.material3_1.0.0",
        "@maven//:androidx.compose.material3.material3_1.2.1",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.runtime.runtime-saveable_1.6.6",
        "@maven//:androidx.compose.runtime.runtime_1.6.6",
        "@maven//:androidx.compose.ui.ui-graphics-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-graphics-android_1.6.6",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-graphics_1.6.6",
        "@maven//:androidx.compose.ui.ui-test-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-test-android_1.6.6",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-test-junit4-android_1.5.0-beta01",
        "@maven//:androidx.compose.ui.ui-test-junit4-android_1.6.6",
        "@maven//:androidx.compose.ui.ui-test-junit4_1.3.0",
        "@maven//:androidx.compose.ui.ui-test-junit4_1.5.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.ui.ui-test-junit4_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-test-junit4_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-test-manifest_1.3.0",
        "@maven//:androidx.compose.ui.ui-test-manifest_1.5.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.ui.ui-test-manifest_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-test-manifest_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-tooling-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-data-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-data-android_1.6.6",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-preview-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-preview-android_1.6.6",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.6.6",
        "@maven//:androidx.compose.ui.ui-tooling_1.5.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.compose.ui.ui-tooling_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-tooling_1.6.6",  # Compose BOM 2024.04.01
        "@maven//:androidx.compose.ui.ui-unit-android_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui-unit-android_1.6.6",
        "@maven//:androidx.compose.ui.ui_1.5.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.compose.ui.ui_1.6.6",  # Necessary for Compose for TV.
        "@maven//:androidx.constraintlayout.constraintlayout_1.1.3",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.4",
        "@maven//:androidx.core.core-ktx_1.0.1",
        "@maven//:androidx.core.core-ktx_1.10.0",  # Compose Activity
        "@maven//:androidx.core.core-ktx_1.10.1",
        "@maven//:androidx.core.core-ktx_1.11.0-alpha04",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.core.core-ktx_1.11.0-beta01",  # Necessary for Compose for TV.
        "@maven//:androidx.core.core-ktx_1.12.0",
        "@maven//:androidx.core.core-ktx_1.2.0",
        "@maven//:androidx.core.core-ktx_1.3.0",
        "@maven//:androidx.core.core-ktx_1.3.2",
        "@maven//:androidx.core.core-ktx_1.6.0",
        "@maven//:androidx.core.core-ktx_1.9.0",
        "@maven//:androidx.core.core-splashscreen_1.0.1",
        "@maven//:androidx.core.core_1.5.0-beta01",
        "@maven//:androidx.core.core_1.5.0-rc02",
        "@maven//:androidx.emoji2.emoji2-views-helper_1.3.0",
        "@maven//:androidx.emoji2.emoji2-views-helper_1.4.0",
        "@maven//:androidx.emoji2.emoji2-views-helper_1.4.0-beta03",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:androidx.fragment.fragment-ktx_1.6.0",
        "@maven//:androidx.fragment.fragment_1.3.0",
        "@maven//:androidx.fragment.fragment_1.3.0-rc01",
        "@maven//:androidx.games.games-activity_1.2.2",
        "@maven//:androidx.leanback.leanback_1.0.0",
        "@maven//:androidx.legacy.legacy-support-v13_1.0.0",
        "@maven//:androidx.legacy.legacy-support-v4_1.0.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-extensions_2.2.0",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-process_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.6.1",
        "@maven//:androidx.media.media_1.6.0",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.6.0",
        "@maven//:androidx.navigation.navigation-fragment_2.6.0",
        "@maven//:androidx.navigation.navigation-safe-args-gradle-plugin_2.6.0",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.6.0",
        "@maven//:androidx.navigation.navigation-ui_2.6.0",
        "@maven//:androidx.palette.palette-ktx_1.0.0",
        "@maven//:androidx.preference.preference_1.2.0",
        "@maven//:androidx.recyclerview.recyclerview_1.3.0",
        "@maven//:androidx.room.room-compiler_2.0.0",
        "@maven//:androidx.room.room-runtime_2.0.0",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:androidx.slice.slice-builders_1.0.0",
        "@maven//:androidx.test.espresso.espresso-core_3.5.1",
        "@maven//:androidx.test.ext.junit_1.1.3",
        "@maven//:androidx.test.ext.junit_1.1.5",
        "@maven//:androidx.test.monitor_1.6.1",
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
        "@maven//:com.android.application.com.android.application.gradle.plugin_7.2.0",
        "@maven//:com.android.application.com.android.application.gradle.plugin_7.4.1",
        "@maven//:com.android.application.com.android.application.gradle.plugin_8.1.0",
        "@maven//:com.android.support.appcompat-v7_25.4.0",
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.constraint.constraint-layout_1.1.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.1.3",
        "@maven//:com.android.support.constraint.constraint-layout_2.0.4",
        "@maven//:com.android.support.multidex-instrumentation_1.0.2",
        "@maven//:com.android.support.multidex_1.0.1",
        "@maven//:com.android.support.recyclerview-v7_27.1.1",
        "@maven//:com.android.support.support-v13_28.0.0",
        "@maven//:com.android.support.support-v4_27.1.1",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.android.tools.build.bundletool_1.17.1",
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:com.android.tools.build.gradle_7.2.0",
        "@maven//:com.android.tools.build.gradle_7.3.1",
        "@maven//:com.android.tools.build.gradle_7.4.1",
        "@maven//:com.android.tools.desugar_jdk_libs_1.0.5",
        "@maven//:com.android.tools.lint.lint-checks_26.5.0",
        "@maven//:com.github.bumptech.glide.glide_4.11.0",
        "@maven//:com.google.android.gms.play-services-ads_21.5.0",
        "@maven//:com.google.android.gms.play-services-base_17.6.0",
        "@maven//:com.google.android.gms.play-services-gass_20.0.0",
        "@maven//:com.google.android.gms.play-services-maps_17.0.1",
        "@maven//:com.google.android.gms.play-services-maps_18.1.0",
        "@maven//:com.google.android.gms.play-services-pay_16.1.0",
        "@maven//:com.google.android.gms.play-services-wallet_19.1.0",
        "@maven//:com.google.android.gms.play-services-wearable_18.0.0",
        "@maven//:com.google.android.horologist.horologist-compose-tools_0.4.8",
        "@maven//:com.google.android.horologist.horologist-tiles_0.4.8",
        "@maven//:com.google.android.libraries.mapsplatform.secrets-gradle-plugin.secrets-gradle-plugin_2.0.1",
        "@maven//:com.google.android.material.material_1.1.0",
        "@maven//:com.google.android.material.material_1.10.0",
        "@maven//:com.google.android.material.material_1.3.0",
        "@maven//:com.google.android.material.material_1.4.0",
        "@maven//:com.google.android.material.material_1.5.0",
        "@maven//:com.google.android.material.material_1.8.0",
        "@maven//:com.google.android.material.material_1.9.0",
        "@maven//:com.google.android.support.wearable_2.9.0",
        "@maven//:com.google.android.wearable.wearable_2.9.0",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.code.gson.gson_2.2.4",
        "@maven//:com.google.code.gson.gson_2.6.2",
        "@maven//:com.google.dagger.dagger-compiler_2.6",
        "@maven//:com.google.dagger.dagger_2.6",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.firebase.firebase-common_12.0.1",
        "@maven//:com.google.guava.guava_28.2-jre",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.google.prefab.cli_2.1.0",
        "@maven//:com.google.protobuf.protobuf-bom_3.17.2",
        "@maven//:com.linkedin.testbutler.test-butler-app_1.3.1",
        "@maven//:com.squareup.okio.okio-jvm_3.0.0",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-codec.commons-codec_1.9",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:commons-logging.commons-logging_1.1.1",
        "@maven//:javax.annotation.javax.annotation-api_1.2",
        "@maven//:javax.annotation.javax.annotation-api_1.3.2",
        "@maven//:javax.inject.javax.inject_1",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.apache.commons.commons-compress_1.21",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.hamcrest.hamcrest-library_1.3",
        "@maven//:org.jdeferred.jdeferred-android-aar_1.2.3",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.8.10",  # Needed for EmptyActivity
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.9.0",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.9.24",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_2.0.0",  # Kotlin/Compose 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-bom_1.8.0",
        "@maven//:org.jetbrains.kotlin.kotlin-bom_1.8.22",
        "@maven//:org.jetbrains.kotlin.kotlin-build-tools-impl_2.0.0",  # Kotlin/Compose 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.kotlin-compiler-embeddable_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compose-compiler-plugin-embeddable_2.0.0",  # Kotlin/Compose 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.9.24",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.9.24",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_2.0.0",  # Kotlin/Compose 2.0.0 templates
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.22",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.9.24",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_2.0.0",
        "@maven//:org.jetbrains.kotlin.kotlin-test-junit",
        "@maven//:org.jetbrains.kotlin.plugin.compose.org.jetbrains.kotlin.plugin.compose.gradle.plugin_2.0.0",  # Kotlin/Compose 2.0.0 templates
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-android_1.7.1",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.7.1",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-test_1.7.1",  # Needed for TemplateTest#testNewComposeWearActivityWithTileAndComplication
        "@maven//:org.mockito.mockito-core_5.12.0",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)

java_test(
    name = "LayoutEditorMemoryUseTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/LayoutTest/**"]) + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.LayoutEditorMemoryUseTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "InspectionsMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.InspectionsMemoryUseTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GradleSyncMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.GradleSyncMemoryUseTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "FindInPathMemoryLeakTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.FindInPathMemoryLeakTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "EditorTabsMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.EditorTabsMemoryUseTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddResourceMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.AddResourceMemoryUseTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddAssetMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.AddAssetMemoryUseTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NavEditorMemoryUseTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/Navigation/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.NavEditorMemoryUseTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VisualizationTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.VisualizationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ProblemsPanelTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/CodeGeneration/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.designTools.problemsPanel.ProblemsPanelTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "SharedIssuePanelTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.SharedIssuePanelTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VisualLintTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/VisualLintApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.VisualLintTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "DownloadableFontTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.DownloadableFontTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ComposePreviewTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/SimpleComposeApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.compose.ComposePreviewTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CustomViewPreviewTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/SimpleComposeApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.customview.CustomViewPreviewTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GuiTestRuleTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.framework.GuiTestRuleTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ResolveXmlReferencesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/DatabindingMethodsTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editing.ResolveXmlReferencesTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewProjectTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NewProjectTest"],
    tags = COMMON_TAGS + ["manual"],  #smoke test passing, but latestSupportLibraryWhileDndLayouts failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewActivityTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.NewActivityTest"],
    tags = COMMON_TAGS + ["manual"],  # b/74910563
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "DeviceConfigurationDialogTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.avdmanager.DeviceConfigurationDialogTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NlEditorTest",
    size = "large",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/sdk:platforms/android-25_build_only",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NlEditorTest"],
    tags = COMMON_TAGS + ["manual"],  #BasicLayoutTest failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NavNlEditorTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/Navigation/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.NavNlEditorTest"],
    tags = COMMON_TAGS + ["manual"],  #BasicLayoutTest failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConstraintLayoutTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ConstraintLayoutTest"],
    tags = COMMON_TAGS + ["manual"],  #Test failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GradleSyncTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.GradleSyncTest"],
    tags = COMMON_TAGS + ["manual"],  #Sanity tests passing, but other tests fail
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VerifyJavaKotlinXmlCodeStylesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.VerifyJavaKotlinXmlCodeStylesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AltEnterSuggestionsTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.AltEnterSuggestionsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GradleBuildTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.GradleBuildTest"],
    tags = COMMON_TAGS + [
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewModuleTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/LocalJarsAsModules/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.NewModuleTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "UpdateBuildTypesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.UpdateBuildTypesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LayoutInspectorTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/LayoutInspector/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.layoutinspector.LayoutInspectorTest"],
    tags = COMMON_TAGS + [
        "manual",
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConvertFromAndToWebpTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/ImportLocalWebpProject/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.ConvertFromAndToWebpTest"],
    tags = COMMON_TAGS + [
        "no_test_mac",  # Cannot be automated in MAC
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "FilterIconTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.FilterIconTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "BuildAnalyzerTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/BuildAttributionApp/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.build.analyzer.BuildAnalyzerTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CodeConversionFromJavaToKotlinTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/JavaToKotlinCode/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.CodeConversionFromJavaToKotlinTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AndroidProfilerTest",
    data = COMMON_DATA + glob(["testData/MinimalMinSdk26Apk/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.profiler.AndroidProfilerTest"],
    tags = COMMON_TAGS + ["manual"],  # b/154963251
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "DeploymentTest",
    size = "large",
    data = COMMON_DATA + [
        "//tools/base/deploy/deployer:test_apks",
    ] + glob([
        "testData/simple/**",
    ]) + select({
        "@platforms//os:windows": [],
        "//conditions:default": [
            "//tools/base/deploy/installer:test-installer",
            "//tools/base/deploy/installer/tests:artifacts",
        ],
    }),
    flaky = True,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.deploy.DeploymentTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "ui_test",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/203243180
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "TerminateAdbIfNotUsedTest",
    data = COMMON_DATA + glob(["testData/simple/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.adb.TerminateAdbIfNotUsedTest"],
    tags = COMMON_TAGS + ["manual"],  # b/154963251
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConsentDialogOptinTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.analytics.ConsentDialogOptinTest -Denable.android.analytics.consent.dialog.for.test=true -Denable.android.analytics.logging.for.test=true"],
    tags = COMMON_TAGS + ["manual"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConsentDialogDeclineTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.analytics.ConsentDialogDeclineTest -Denable.android.analytics.consent.dialog.for.test=true -Denable.android.analytics.logging.for.test=false"],
    tags = COMMON_TAGS + ["manual"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LoggingEnabledTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.analytics.LoggingEnabledTest -Denable.android.analytics.logging.for.test=true"],
    tags = COMMON_TAGS + ["manual"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LoggingDisabledTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.analytics.LoggingDisabledTest -Denable.android.analytics.logging.for.test=false"],
    tags = COMMON_TAGS + ["manual"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "EmbeddedEmulatorTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/android-emulator:emulator",
    ] + glob(["testData/TextInput/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.end2end.EmbeddedEmulatorTest"],
    tags = COMMON_TAGS + ["manual"],  # b/154963251
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CompileWithJava8Test",
    data = COMMON_DATA + glob([
        "testData/MinSdk24App/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.java8.CompileWithJava8Test"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",  # fails 0 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "RoomQueryCodeCompletionTest",
    data = COMMON_DATA + glob([
        "testData/RoomSampleProject/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.jetpack.RoomQueryCodeCompletionTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ViewModelAndLiveDataTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/ViewModelLiveDataSample/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.jetpack.ViewModelAndLiveDataTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateDefaultActivityTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateDefaultActivityTest"],
    tags = COMMON_TAGS + [
        "qa_smoke",  # fails 0 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GenerateApkWithReleaseVariantTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.GenerateApkWithReleaseVariantTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",  # b/110178024
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ModifyMinSdkAndSyncTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.ModifyMinSdkAndSyncTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",  # fails 1 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNewAppModuleWithDefaultsTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.CreateNewAppModuleWithDefaultsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNewLibraryModuleWithDefaultsTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.CreateNewLibraryModuleWithDefaultsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddNewBuildTypeTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AddNewBuildTypeTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddSupportLibraryTest",
    size = "large",
    timeout = "long",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/ConvertFrom9Patch/**",
    ]) + glob([
        "testData/VersionCatalogProject/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AddSupportLibraryTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "StudioDefaultJDKTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.newpsd.StudioDefaultJDKTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LinkProjectWithKotlinTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/LinkProjectWithKotlin/**",
    ]),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.LinkProjectWithKotlinTest",
    ],
    tags = COMMON_TAGS + [
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [
        ":intellij.android.guiTests_testlib",
    ],
)

java_test(
    name = "AddKotlinFilesTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/LinkProjectWithKotlin/**",
    ]),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.AddKotlinFilesTest",
    ],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [
        ":intellij.android.guiTests_testlib",
    ],
)

java_test(
    name = "RunAppEnableTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.RunAppEnableTest",
    ],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [
        ":intellij.android.guiTests_testlib",
    ],
)

java_test(
    name = "SideConstraintHandlingTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.SideConstraintHandlingTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "BaselineConstraintHandlingTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.BaselineConstraintHandlingTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "BasicLayoutEditTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    flaky = True,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.BasicLayoutEditTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
        "ui_test",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ComponentSelectionSplitViewTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ComponentSelectionSplitViewTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "ui_test",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "OpenCloseVisualizationToolTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.OpenCloseVisualizationToolTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "FlavorsExecutionTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/SimpleFlavoredApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.FlavorsExecutionTest"],
    tags = COMMON_TAGS + [
        "manual",
        "noci:studio-win",
        "qa_smoke",
        "qa_smoke_unreliable",  # b/160247726
        "qa_unreliable",  # b/160247726
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNewFlavorsTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/sdk:platforms/android-24_build_only",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.CreateNewFlavorsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddDebugAndReleaseSourceSetToProjectTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AddDebugAndReleaseSourceSetToProjectTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GradleVersionCatalogTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/VersionCatalogProject/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.GradleVersionCatalogTest"],
    tags = COMMON_TAGS + [
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GradleVersionCatalogTest2",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/VersionCatalogProject/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.GradleVersionCatalogTest2"],
    tags = COMMON_TAGS + [
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LaunchApkViewerTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.LaunchApkViewerTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateBasicKotlinProjectTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.CreateBasicKotlinProjectTest",
    ],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddRemoveCppDependencyTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.AddRemoveCppDependencyTest",
    ],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNewProjectWithCpp1Test",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateNewProjectWithCpp1Test"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",  # fails 1 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNewProjectWithCpp2Test",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateNewProjectWithCpp2Test"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",  # fails 1 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNewProjectWithCpp3Test",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateNewProjectWithCpp3Test"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",  # fails 0 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewModuleWithVersionCatalogTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleAndroidxApplicationWithConventionalDefaultToml/**",
    ]) + glob([
        "testData/SimpleAndroidxApplicationVersionCatalogInBuildScript/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.versioncatalog.NewModuleWithVersionCatalogTest"],
    tags = COMMON_TAGS + [
        "manual",
        "ui_test",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ModuleVariantConflictTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/ModuleVariantConflicts/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.ModuleVariantConflictTest"],
    tags = COMMON_TAGS + [
        "manual",
        "ui_test",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateCppKotlinProjectTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.CreateCppKotlinProjectTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "BuildCppKotlinTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/CppKotlin/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.BuildCppKotlinTest"],
    tags = COMMON_TAGS + [
        "no_crostini",
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AbiSplitApksTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.AbiSplitApksTest"],
    tags = COMMON_TAGS + [
        "manual",
        "no_crostini",
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "X86AbiSplitApksTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.X86AbiSplitApksTest"],
    tags = COMMON_TAGS + [
        "manual",
        "no_crostini",
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LocalApkProjTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/ApkDebug/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.LocalApkProjTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "InstallPackageTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.sdkmanager.InstallPackageTest"],
    tags = [
        "cpu:100",
        "no-remote",
        "noci:studio-win",
        "qa_smoke",
        "qa_smoke_emu",  # Run together with emulators because the test needs to run alone
        "requires-network",
        "requires_emulator",  # Run together with emulators because the test needs to run alone
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ImportTestHistoryTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.androidtest.ImportTestHistoryTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "noci:studio-linux",
        "ci:studio-linux_very_flaky",  # b/269167918
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

config_setting(
    name = "has_emulator",
    define_values = {
        "external_emulator": "true",
    },
)

java_test(
    name = "DebugOnEmulatorTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]) + [
        "//prebuilts/studio/sdk:platforms/android-23_build_only",
    ] + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.DebugOnEmulatorTest",
    ],
    # do not sort: the beginning of this list is copied from COMMON_TAGS
    tags = [
        "block-network",
        "noci:studio-win",
        "qa_smoke",
        "qa_smoke_emu",
        "requires_emulator",
        "cpu:100",
        "qa_smoke_unreliable_emu",  # intend to run it here to see stable smoke ui tests running time
        "qa_unreliable",  # intend to run it here to see stable smoke ui tests running time
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNavGraphTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.CreateNavGraphTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNewActivityFromNavGraphTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.CreateNewActivityFromNavGraphTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ManuallyAddNavLibTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.ManuallyAddNavLibTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NavGraphSanityTest",
    size = "large",
    timeout = "long",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.NavGraphSanityTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddAllLayoutItemsFromPaletteTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.AddAllLayoutItemsFromPaletteTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddOrRemoveAttributesTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.AddOrRemoveAttributesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ComponentSelectionTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ComponentSelectionTest"],
    tags = COMMON_TAGS + ["manual"],  # b/267327815,
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateNavNIGraphTest",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.CreateNavNIGraphTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NavigateToLayoutFromNavGraphTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.NavigateToLayoutFromNavGraphTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "FragmentAttributeSectionsValidationTest",
    size = "large",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "@maven//:com.android.support.support-annotations_28.0.0",
        "@maven//:com.android.support.appcompat-v7_26.1.0",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.FragmentAttributeSectionsValidationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# TODO(b/331230661): Enable the test once the bug is fixed in linux, mac and chrome

java_test(
    name = "DualDebuggerBreakpointsTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.DualDebuggerBreakpointsTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_smoke",  # b/128932526
        "qa_smoke_emu",  # b/128932526
        "qa_smoke_unreliable_emu",  # b/128932526
        "qa_unreliable",  # b/128932526
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "RunInstrumentationTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/InstrumentationTest/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.RunInstrumentationTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_smoke",
        "qa_smoke_emu",
        "qa_smoke_unreliable",  # b/145991485
        "qa_unreliable",  # b/145991485
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "EspressoRecorderTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.espresso.EspressoRecorderTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_smoke",
        "qa_smoke_emu",
        "qa_smoke_unreliable_emu",  # b/77635374
        "qa_unreliable",  # b/77635374
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CMakeListsTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/debugger/CMakeListsHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.CMakeListsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewComposeProjectTest",
    size = "large",
    data = COMMON_DATA,
    flaky = True,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.compose.NewComposeProjectTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddComposeMultiPreviewTest",
    size = "large",
    timeout = "long",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.compose.AddComposeMultiPreviewTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CyclicMultiPreviewTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/ComposeMultiPreviewSample/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.compose.CyclicMultiPreviewTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddNewPreviewTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/ComposeMultiPreviewSample/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.compose.AddNewPreviewTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "RemovePreviewTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.compose.RemovePreviewTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "QuickFixForJniTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/debugger/CLionNdkHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.QuickFixForJniTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "WarningsCheckInNewCPPProjectTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/debugger/CLionNdkHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.WarningsCheckInNewCPPProjectTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "OpenExistingProjectTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.OpenExistingProjectTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CodeGenerationTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/CodeGeneration/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.intellijplatform.CodeGenerationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GotoDeclarationTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/CodeGeneration/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.intellijplatform.GotoDeclarationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "QuickDocumentationTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.intellijplatform.QuickDocumentationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "QuickDefinitionTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.intellijplatform.QuickDefinitionTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ReformatFileTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.intellijplatform.ReformatFileTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "DaggerSymbolResolutionTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/Dagger2App/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.annotator.DaggerSymbolResolutionTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "SupportNewAnnotationsTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.intellijplatform.SupportNewAnnotationsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddBenchmarkModuleTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleAndroidxApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.benchmark.AddBenchmarkModuleTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_unreliable",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LintTest",
    data = COMMON_DATA + glob([
        "testData/LintTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.lint.LintTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",  # b/110178024
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VerifyNpwPhoneAndTabletTemplatesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.VerifyNpwPhoneAndTabletTemplatesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VerifyNpwPhoneAndTabletCppTemplatesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.VerifyNpwPhoneAndTabletCppTemplatesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VerifyNpwPhoneAndTabletNavigationTemplatesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.VerifyNpwPhoneAndTabletNavigationTemplatesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VerifyNpwPhoneAndTabletViewsTemplatesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.VerifyNpwPhoneAndTabletViewsTemplatesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VerifyNpwWearOSTemplatesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.VerifyNpwWearOSTemplatesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VerifyNpwAutomotiveTemplatesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob(["testData/Navigation/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.VerifyNpwAutomotiveTemplatesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VerifyNpwAndroidTVTemplatesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.VerifyNpwAndroidTVTemplatesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "UnusedResourceEvaluationTest",
    data = COMMON_DATA + glob([
        "testData/NoUnusedResourceApp/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.UnusedResourceEvaluationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "UnusedResourceEvaluationTest2",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.UnusedResourceEvaluationTest2"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "MultiModuleSearchTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/MultiAndroidModule/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.resourceexplorer.MultiModuleSearchTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ResourceExplorerTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/ResourceManagerApp/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.resourceexplorer.ResourceExplorerTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "OpenFileInResourceManagerTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
    ] + glob([
        "testData/MultiAndroidModule/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.resourceexplorer.OpenFileInResourceManagerTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "DrawableResourceNavigationTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
    ] + glob([
        "testData/DrawableResource/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.resourceexplorer.DrawableResourceNavigationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ImageViewResourceTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.resourceexplorer.ImageViewResourceTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "JavaLibsDepTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.JavaLibsDepTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
        "qa_fast_unreliable",  # b/117238132
        "qa_unreliable",  # b/117238132
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AndroidLibsDepTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AndroidLibsDepTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
        "qa_fast_unreliable",  # b/117238132
        "qa_unreliable",  # b/117238132
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "JavaDepTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "@maven//:com.google.code.gson.gson_2.6.2",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.JavaDepTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
        "qa_fast_unreliable",  # b/117238132
        "qa_unreliable",  # b/117238132
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AndroidDepTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "@maven//:com.google.code.gson.gson_2.6.2",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AndroidDepTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
        "qa_fast_unreliable",  # b/111889561
        "qa_unreliable",  # b/111889561
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "PrivateResourceTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/PrivateResource/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editing.PrivateResourceTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ChangeLibModSettingsTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/PrivateResource/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ChangeLibModSettingsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
        "qa_fast_unreliable",  # b/111889561
        "qa_unreliable",  # b/111889561
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NameWithSpaceAndDollarTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NameWithSpaceAndDollarTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ThemeEditorTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/sdk:platforms/android-25_build_only",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ThemeEditorTest"],
    tags = COMMON_TAGS + [
        "manual",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConstraintLayoutResizeHandleTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ConstraintLayoutResizeHandleTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConstraintLayoutAnchorExemptionTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ConstraintLayoutAnchorExemptionTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ClearConstraintTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ClearConstraintTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
        "qa_fast_unreliable",  # b/116801839
        "qa_unreliable",  # b/116801839
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateBaselineConnectionTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.CreateBaselineConnectionTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AdaptiveIconsTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.assetstudio.AdaptiveIconsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AndroidVectorDrawableToolTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.AndroidVectorDrawableToolTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ImageAssetGradleTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ImageAssetGradleTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
        "qa_fast_unreliable",  # b/115567466
        "qa_unreliable",  # b/115567466
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ImageAssetErrorCheckTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/TestImages/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ImageAssetErrorCheckTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddCppToModuleActionTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.AddCppToModuleActionTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ShowFilesUnknownToCMakeActionTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.ShowFilesUnknownToCMakeActionTest"],
    tags = COMMON_TAGS + [
        "no_crostini",
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CLionIntegrationTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/debugger/CLionNdkHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.CLionIntegrationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ShortcutNavigationTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/debugger/NdkHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.ShortcutNavigationTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "FlavorsEditingTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/sdk:platforms/android-24_build_only",
        "//prebuilts/studio/sdk:platforms/android-25_build_only",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.FlavorsEditingTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConvertFrom9PatchTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/ConvertFrom9Patch/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.ConvertFrom9PatchTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LintCheckWithKotlinTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/KotlinInstrumentation/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.LintCheckWithKotlinTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NoGradleSyncForProjectReimportTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.NoGradleSyncForProjectReimportTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ApkViewerTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ApkViewerTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "BuildAndRunCMakeProjectTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.BuildAndRunCMakeProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "JavaDebuggerTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.JavaDebuggerTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AutoDebuggerInNdkProjectTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/NdkHelloJni/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.AutoDebuggerInNdkProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_unreliable_emu",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "DualDebuggerInNdkProjectTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/NdkHelloJni/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.DualDebuggerInNdkProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",
        "qa_unreliable",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NativeDebuggerInNdkProjectTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/NdkHelloJni/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.NativeDebuggerInNdkProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",
        "qa_unreliable",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "WatchpointTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/WatchpointTestAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.WatchpointTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",
        "qa_unreliable",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NativeDebuggerBreakpointsTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.NativeDebuggerBreakpointsTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",  # b/137669728
        "qa_unreliable",  # b/137669728
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "SessionRestartTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.SessionRestartTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",  # b/137669728
        "qa_unreliable",  # b/137669728
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "SmartStepIntoTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/debugger/SmartStepIntoCmakeApp/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.SmartStepIntoTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",  # b/138743860
        "qa_unreliable",  # b/138743860
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NdkSxsTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.NdkSxsTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "JavaToKotlinConversionTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.JavaToKotlinConversionTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateFromPreexistingApkTest",
    data = COMMON_DATA + glob([
        "testData/ApkDebug/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.CreateFromPreexistingApkTest"],
    tags = COMMON_TAGS + [
        "manual",
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AgpUpgradeTest",
    size = "large",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.AgpUpgradeTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_smoke",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AgpUpgradeJava8ProjTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/CodeGeneration/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.AgpUpgradeJava8ProjTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CreateAPKProjectTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/ApkDebug/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/python/linux-x86",
        "//prebuilts/tools:lldb",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.CreateAPKProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "InstantAppRunFromCmdLineTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/TopekaInstantApp/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.InstantAppRunFromCmdLineTest",
    ],
    # do not sort: the beginning of this list is copied from COMMON_TAGS
    tags = [
        "block-network",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "requires_emulator",
        "cpu:100",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ImportAndRunInstantAppTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/InstantAppsService/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.ImportAndRunInstantAppTest",
    ],
    # do not sort: the beginning of this list is copied from COMMON_TAGS
    tags = [
        "block-network",
        "noci:studio-win",
        "slow",
        "qa_fast",
        "qa_fast_emu",
        "requires_emulator",
        "cpu:100",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "InstrumentationTest",
    size = "enormous",
    timeout = "long",
    data = COMMON_DATA + glob([
        "testData/KotlinInstrumentation/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:emulator",
            "@externsdk//:system-images",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.InstrumentationTest"],
    tags = [
        "block-network",
        "cpu:100",
        "noci:studio-win",
        "qa_fast",
        "qa_fast_emu",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "InstantAppDynamicModuleOnDemandTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.InstantAppDynamicModuleOnDemandTest",
    ],
    tags = [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "DynamicModuleInstallTimeTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.DynamicModuleInstallTimeTest",
    ],
    tags = [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "EnableInstantAppTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.EnableInstantAppTest",
    ],
    tags = [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddInstantModuleTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.AddInstantModuleTest",
    ],
    tags = [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "BuildInstantAppTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.BuildInstantAppTest",
    ],
    tags = [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "InferNullityTest",
    size = "large",
    data = COMMON_DATA + [
        "@maven//:androidx.annotation.annotation_1.5.0",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.InferNullityTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "AddAppCompatLibTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/NoAppCompatLibApp/**",
    ]),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AddAppCompatLibTest",
        "-Daspects.agent.jar=$(location //tools/base/aspects_agent:aspects_agent)",
        "-Daspects.agent.rules=$(location //tools/adt/idea/android-uitests:aspect_rules)",
        "-Daspects.agent.baseline=$(location //tools/adt/idea/android-uitests:aspects_baseline)",
    ],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "CreateNewGoogleActivitiesTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateNewGoogleActivitiesTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "CreateLoginActivityTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateLoginActivityTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "CreateSettingsActivityTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateSettingsActivityTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "AddLocaleTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.translations.AddLocaleTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "SurroundWithShortcutTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.SurroundWithShortcutTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "ForeachLiveTemplateTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ForeachLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "ForiLiveTemplateTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ForiLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "ToastLiveTemplateTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ToastLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "ImportMlModelTest",
    data = COMMON_DATA + [
        "//prebuilts/tools/common/mlkit/testData",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.mlmodels.ImportMlModelTest"],
    tags = COMMON_TAGS + [
        "noci:studio-win",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)
