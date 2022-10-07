load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/adt/idea/adt-testutils:old-agp-test.bzl", "old_agp_test")
load("//tools/adt/idea/old-agp-tests:agp_versions.bzl", "AGP_7_1", "AGP_DATA")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.build-attribution",
    # do not sort: must match IML order
    srcs = [
        "resources",
        "src",
    ],
    iml_files = ["intellij.android.build-attribution.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/build-analyzer/common:studio.android.build-analyzer.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/build-attribution:build-analysis-results-proto",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.build-attribution.tests",
    iml_files = ["intellij.android.build-attribution.tests.iml"],
    split_test_targets = {
        "analyzers": {
            "shard_count": 2,
            "test_filter": "com.android.build.attribution.analyzers",
            "data": [
                "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
                "//tools/base/build-system:android_gradle_plugin.zip",
                "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
            ],
        },
        "other": {
            "data": [
                "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
                "//tools/base/build-system:android_gradle_plugin.zip",
                "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
            ],
        },
    },
    tags = [
        "no_test_mac",  # b/113099009
        "no_test_windows",  # b/135665870
    ],
    test_class = "com.android.build.attribution.AndroidBuildAttributionTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/build-attribution/testData",
        "//tools/base/build-system:gradle-distrib",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/build-analyzer/common:studio.android.build-analyzer.common[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
        "//tools/adt/idea/build-attribution:intellij.android.build-attribution[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/build-attribution:build-analysis-results-proto",
    ],
)

old_agp_test(
    name = "ConfigurationCachingCompatibilityAnalyzerTest",
    timeout = "long",
    agp_version = AGP_7_1,
    data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/build-attribution/testData",
        "//tools/base/build-system:gradle-distrib",
    ] + AGP_DATA[AGP_7_1],
    gradle_version = "LATEST",
    ignore_other_tests = True,
    iml_module = ":intellij.android.build-attribution.tests",
    maven_deps = [
        "//tools/base/build-system/previous-versions:7.1.0",
        "//tools/base/build-system/previous-versions:kgp1.3.72",
        ":test_deps",
    ],
    tags = [
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
    test_class = "com.android.build.attribution.AndroidBuildAttributionOldAgpTestSuite",
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.collection.collection_1.0.0",
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.google.auto.value.auto-value-annotations_1.6.2",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.code.gson.gson_2.8.5",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.guava.guava_19.0",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:de.undercouch.gradle-download-task_4.0.2",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.5.0",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "build-analysis-results-proto",
    jars = ["//tools/adt/idea/build-attribution/proto:build-analysis-results-proto.jar"],
    visibility = ["//visibility:public"],
)
