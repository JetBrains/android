load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load(":agp_versions.bzl", "AGP_3_1_4", "AGP_3_3_2", "AGP_3_5", "AGP_4_0", "AGP_4_1", "AGP_4_2", "AGP_7_0", "AGP_7_1", "AGP_7_2", "AGP_7_3", "AGP_7_4", "GRADLE_5_3_1", "GRADLE_5_5", "GRADLE_6_7_1", "GRADLE_7_0_2", "GRADLE_7_2", "GRADLE_7_3_3", "GRADLE_7_4", "GRADLE_7_5", "GRADLE_LATEST", "local_old_agp_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.old-agp-tests",
    iml_files = ["intellij.android.old-agp-tests.iml"],
    # keep sorted
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/sdk:build-tools/28.0.3",
        "//prebuilts/studio/sdk:build-tools/29.0.2",
        "//prebuilts/studio/sdk:build-tools/30.0.2",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:cmake",
        "//prebuilts/studio/sdk:docs",
        "//prebuilts/studio/sdk:licenses",
        "//prebuilts/studio/sdk:ndk",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-28",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:platforms/latest-preview",
        "//prebuilts/studio/sdk:sources",
        "//prebuilts/tools/common/m2:org.jetbrains.kotlin.kotlin-gradle-plugin.1.5.21",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:gradle-distrib-5.3.1",
        "//tools/base/build-system:gradle-distrib-5.5",
        "//tools/base/build-system:gradle-distrib-6.7.1",
        "//tools/base/build-system:gradle-distrib-7.0.2",
        "//tools/base/build-system:gradle-distrib-7.2",
        "//tools/base/build-system:gradle-distrib-7.3.3",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
        "//tools/base/build-system/previous-versions:3.3.2",
        "//tools/base/build-system/previous-versions:3.5.0",
        "//tools/base/build-system/previous-versions:4.0.0",
        "//tools/base/build-system/previous-versions:4.1.0",
        "//tools/base/build-system/previous-versions:4.2.0",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
        "//tools/external/gradle:gradle-distrib-2.6",
        "//tools/external/gradle:gradle-distrib-3.5",
        "//tools/external/gradle:gradle-distrib-4.5",
    ],
    test_srcs = ["testSrc"],
    # Tests are run using explicit, local_old_agp_test targets
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/project-system-gradle-repository-search:intellij.android.projectSystem.gradle.repositorySearch[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle.tests[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.annotation.annotation_1.2.0-rc01",
        "@maven//:androidx.appcompat.appcompat_1.0.2",
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.constraintlayout.constraintlayout_1.1.3",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.core.core-ktx_1.0.1",
        "@maven//:androidx.core.core-ktx_1.2.0",
        "@maven//:androidx.core.core-ktx_1.6.0",
        "@maven//:androidx.core.core_1.5.0-beta01",
        "@maven//:androidx.core.core_1.5.0-rc02",
        "@maven//:androidx.lifecycle.lifecycle-common_2.3.0-rc01",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.3.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.3.1",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.3.5",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.3.5",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:androidx.test.espresso.espresso-core_3.1.0",
        "@maven//:androidx.test.espresso.espresso-core_3.2.0",
        "@maven//:androidx.test.ext.junit_1.1.2",
        "@maven//:androidx.test.ext.junit_1.1.3-alpha02",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.constraint.constraint-layout_1.1.0",
        "@maven//:com.android.support.design_28.0.0",
        "@maven//:com.android.support.support-v13_28.0.0",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.google.android.material.material_1.4.0",
        "@maven//:com.google.auto.value.auto-value-annotations_1.6.2",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.code.findbugs.jsr305_1.3.9",
        "@maven//:com.google.code.gson.gson_2.2.4",
        "@maven//:com.google.code.gson.gson_2.8.0",
        "@maven//:com.google.dagger.dagger_2.6",
        "@maven//:com.google.guava.guava_19.0",
        "@maven//:com.google.guava.guava_23.0",
        "@maven//:com.google.guava.guava_28.1-jre",
        "@maven//:com.google.truth.truth_0.44",
        "@maven//:com.linkedin.testbutler.test-butler-app_1.3.1",
        "@maven//:commons-codec.commons-codec_1.9",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:it.unimi.dsi.fastutil_7.2.0",
        "@maven//:junit.junit_4.13.2",
        "@maven//:net.sf.proguard.proguard-gradle_6.0.3",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.14",
        "@maven//:org.jacoco.org.jacoco.ant_0.8.7",
        "@maven//:org.jacoco.org.jacoco.ant_0.8.8",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.5.21",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.5.21",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.31",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.5.21",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.5.21",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.4.31",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.5.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.5.21",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-android_1.5.2",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-jvm_1.5.2",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.5.2",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)

local_old_agp_test(
    agp_version = AGP_3_1_4,
    gradle_version = GRADLE_5_3_1,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_3_3_2,
    gradle_version = GRADLE_5_3_1,
    shard_count = 1,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_3_3_2,
    gradle_version = GRADLE_5_5,
    shard_count = 1,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_3_5,
    gradle_version = GRADLE_5_5,
    shard_count = 4,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_4_0,
    gradle_version = GRADLE_6_7_1,
    shard_count = 3,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_4_1,
    gradle_version = GRADLE_6_7_1,
    shard_count = 3,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_4_2,
    gradle_version = GRADLE_6_7_1,
    shard_count = 4,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_7_0,
    gradle_version = GRADLE_7_0_2,
    shard_count = 3,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_7_1,
    gradle_version = GRADLE_7_2,
    shard_count = 3,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_7_2,
    gradle_version = GRADLE_7_3_3,
    shard_count = 5,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_7_3,
    gradle_version = GRADLE_7_4,
    shard_count = 5,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)

local_old_agp_test(
    agp_version = AGP_7_4,
    gradle_version = GRADLE_7_5,
    shard_count = 5,
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
)
