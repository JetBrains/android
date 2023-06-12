load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lint",
    srcs = ["src"],
    iml_files = ["intellij.android.lint.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/lint:intellij.lint[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-toml",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//tools/adt/idea/assistant[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lint.tests",
    iml_files = ["intellij.android.lint.tests.iml"],
    split_test_targets = {
        "AndroidLintCustomCheckTest": {
            "test_filter": "com.android.tools.idea.lint.AndroidLintCustomCheckTest",
        },
        "LintModelSnapshotComparisonTest": {
            # keep sorted
            "data": [
                "//prebuilts/studio/sdk:cmake",
                "//prebuilts/studio/sdk:ndk",
            ],
            "tags": ["no_windows"],
            "test_filter": "com.android.tools.idea.lint.model.LintModelSnapshotComparisonTest",
        },
        "AndroidLintGradleTest": {
            "test_filter": "com.android.tools.idea.lint.AndroidLintGradleTest",
        },
        "other": {},
    },
    test_class = "com.android.tools.idea.lint.AndroidLintTestSuite",
    # keep sorted
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android-lint/testData",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    test_srcs = ["testSrc"],
    test_tags = [],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/repository:studio.android.sdktools.repository[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module, test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/jps-model:intellij.android.jps.model[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle[test]",
        "//tools/base/lint/libs/lint-tests:studio.android.sdktools.lint.testinfrastructure[module, test]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.appcompat.appcompat_1.0.2",
        "@maven//:androidx.appcompat.appcompat_1.1.0",
        "@maven//:androidx.constraintlayout.constraintlayout_1.1.3",
        "@maven//:androidx.core.core-ktx_1.0.1",
        "@maven//:androidx.test.espresso.espresso-core_3.2.0",
        "@maven//:androidx.test.ext.junit_1.1.2",
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.constraint.constraint-layout_1.1.0",
        "@maven//:com.android.support.multidex-instrumentation_1.0.2",
        "@maven//:com.android.support.support-v13_28.0.0",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.android.tools.desugar_jdk_libs_1.1.5",
        "@maven//:com.google.code.gson.gson_2.2.4",
        "@maven//:com.google.dagger.dagger-compiler_2.6",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.1",
        "@maven//:com.google.guava.guava_27.0.1-android",
        "@maven//:com.google.guava.guava_28.2-jre",
        "@maven//:com.google.truth.truth_0.44",
        "@maven//:com.googlecode.java-diff-utils.diffutils_1.3.0",
        "@maven//:junit.junit_4.13.2",
        "@maven//:org.checkerframework.checker-compat-qual_2.5.5",
        "@maven//:org.jdeferred.jdeferred-android-aar_1.2.3",
    ],
)
