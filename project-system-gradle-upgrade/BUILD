load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/adt/idea/adt-testutils:old-agp-test.bzl", "old_agp_test")
load("//tools/adt/idea/old-agp-tests:agp_versions.bzl", "AGP_4_0", "AGP_4_1", "AGP_4_2", "AGP_7_0", "AGP_7_1", "AGP_DATA")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.upgrade.tests",
    iml_files = ["intellij.android.projectSystem.gradle.upgrade.tests.iml"],
    split_test_targets = {
        "integration": {
            "test_filter": "com.android.tools.idea.gradle.project.upgrade.integration",
            # Tests are run using explicit, local_old_agp_test targets
            "tags": ["manual"],
        },
        "other": {
        },
    },
    test_class = "com.android.tools.idea.gradle.project.upgrade.GradleProjectSystemUpgradeTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/project-system-gradle-upgrade/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
    ],
    test_srcs = ["testSrc"],
    test_tags = [],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/observable:intellij.android.observable[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle.tests[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.upgrade",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.gradle.upgrade.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module]",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.annotation.annotation_1.2.0-rc01",
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.core.core-ktx_1.6.0",
        "@maven//:androidx.lifecycle.lifecycle-common_2.3.0-rc01",
        "@maven//:androidx.navigation.navigation-fragment-ktx_2.3.5",
        "@maven//:androidx.navigation.navigation-ui-ktx_2.3.5",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:androidx.test.espresso.espresso-core_3.2.0",
        "@maven//:androidx.test.ext.junit_1.1.2",
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.android.support.test.runner_1.0.2",
        "@maven//:com.google.android.material.material_1.4.0",
        "@maven//:com.google.auto.value.auto-value-annotations_1.6.2",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.code.findbugs.jsr305_1.3.9",
        "@maven//:com.google.code.gson.gson_2.8.0",
        "@maven//:com.google.code.gson.gson_2.8.5",
        "@maven//:com.google.guava.guava_19.0",
        "@maven//:com.google.guava.guava_28.1-jre",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:de.undercouch.gradle-download-task_4.0.2",
        "@maven//:it.unimi.dsi.fastutil_7.2.0",
        "@maven//:net.sf.proguard.proguard-gradle_6.0.3",
        "@maven//:org.apache.commons.commons-compress_1.12",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.6.21",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.6.21",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.31",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.6.21",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.6.21",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.4.31",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.6.21",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)

# Tests can't currently run on windows because full jar path is passed by the rule instead of just jar name like in iml rule.
old_agp_test(
    name = "ProjectUpgradeTests_From",
    timeout = "long",
    agp_version = AGP_4_0,
    data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/project-system-gradle-upgrade/testData",
        "//tools/base/build-system:gradle-distrib-6.1.1",
    ] + AGP_DATA[AGP_4_0],
    gradle_version = "6.1.1",
    ignore_other_tests = True,
    iml_module = ":intellij.android.projectSystem.gradle.upgrade.tests",
    maven_deps = [
        ":test_deps",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system/previous-versions:4.0.0",
        "//tools/base/build-system/previous-versions:kgp1.3.72",
    ],
    tags = [
        "block-network",
        "no_test_mac",  # b/215483578
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.gradle.project.upgrade.integration.GradleProjectSystemUpgradeIntegrationTestSuite",
)

old_agp_test(
    name = "ProjectUpgradeTests_From",
    timeout = "long",
    agp_version = AGP_4_1,
    data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/project-system-gradle-upgrade/testData",
        "//tools/base/build-system:gradle-distrib-6.5",
    ] + AGP_DATA[AGP_4_1],
    gradle_version = "6.5",
    ignore_other_tests = True,
    iml_module = ":intellij.android.projectSystem.gradle.upgrade.tests",
    maven_deps = [
        ":test_deps",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system/previous-versions:4.1.0",
        "//tools/base/build-system/previous-versions:kgp1.3.72",
    ],
    tags = [
        "block-network",
        "no_test_mac",  # b/215483578
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.gradle.project.upgrade.integration.GradleProjectSystemUpgradeIntegrationTestSuite",
)

old_agp_test(
    name = "ProjectUpgradeTests_From",
    timeout = "long",
    agp_version = AGP_4_2,
    data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/project-system-gradle-upgrade/testData",
        "//tools/base/build-system:gradle-distrib-6.7.1",
    ] + AGP_DATA[AGP_4_2],
    gradle_version = "6.7.1",
    ignore_other_tests = True,
    iml_module = ":intellij.android.projectSystem.gradle.upgrade.tests",
    maven_deps = [
        ":test_deps",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system/previous-versions:4.2.0",
        "//tools/base/build-system/previous-versions:kgp1.3.72",
    ],
    tags = [
        "block-network",
        "no_test_mac",  # b/215483578
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.gradle.project.upgrade.integration.GradleProjectSystemUpgradeIntegrationTestSuite",
)

old_agp_test(
    name = "ProjectUpgradeTests_From",
    timeout = "long",
    agp_version = AGP_7_0,
    data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData:snapshots",
        "//tools/adt/idea/project-system-gradle-upgrade/testData",
        "//tools/base/build-system:gradle-distrib-7.0.2",
    ] + AGP_DATA[AGP_7_0],
    gradle_version = "7.0.2",
    ignore_other_tests = True,
    iml_module = ":intellij.android.projectSystem.gradle.upgrade.tests",
    maven_deps = [
        ":test_deps",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system/previous-versions:7.0.0",
        "//tools/base/build-system/previous-versions:kgp1.3.72",
    ],
    tags = [
        "block-network",
        "no_test_mac",  # b/215483578
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.gradle.project.upgrade.integration.GradleProjectSystemUpgradeIntegrationTestSuite",
)

old_agp_test(
    name = "ProjectUpgradeTests_From",
    timeout = "long",
    agp_version = AGP_7_1,
    data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/project-system-gradle-upgrade/testData",
        "//tools/base/build-system:gradle-distrib-7.2",
    ] + AGP_DATA[AGP_7_1],
    gradle_version = "7.2",
    ignore_other_tests = True,
    iml_module = ":intellij.android.projectSystem.gradle.upgrade.tests",
    maven_deps = [
        ":test_deps",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system/previous-versions:7.1.0",
        "//tools/base/build-system/previous-versions:kgp1.3.72",
    ],
    tags = [
        "block-network",
        "no_test_mac",  # b/215483578
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.gradle.project.upgrade.integration.GradleProjectSystemUpgradeIntegrationTestSuite",
)

old_agp_test(
    name = "ProjectUpgradeTests_From",
    timeout = "long",
    agp_version = "LATEST",
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/project-system-gradle-upgrade/testData",
        "//tools/base/build-system:gradle-distrib",
    ],
    gradle_version = "LATEST",
    ignore_other_tests = True,
    iml_module = ":intellij.android.projectSystem.gradle.upgrade.tests",
    maven_deps = [
        ":test_deps",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
    ],
    tags = [
        "block-network",
        "no_test_mac",  # b/215483578
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.gradle.project.upgrade.integration.GradleProjectSystemUpgradeIntegrationTestSuite",
)
