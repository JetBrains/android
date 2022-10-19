load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.safeargs",
    srcs = ["src"],
    iml_files = ["intellij.android.nav.safeargs.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.safeargs.tests",
    iml_files = ["intellij.android.nav.safeargs.tests.iml"],
    split_test_targets = {
        "gradle": {
            "tags": ["cpu:3"],
            "shard_count": 2,
            "test_filter": ".gradle",
            "data": [
                # keep sorted
                ":testdeps_repo",
                "//prebuilts/studio/sdk:build-tools/latest",
                "//prebuilts/studio/sdk:platforms/latest",
                "//tools/base/build-system:android_gradle_plugin.zip",
                "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
                "//tools/base/build-system:gradle-distrib",
                "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
            ],
        },
        "non-gradle": {},
    },
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.nav.safeargs.SafeArgsTestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/nav/safeargs/testData",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito",
    ],
)

maven_repository(
    # Dependencies needed for on-disk test projects, e.g. Gradle tests
    name = "testdeps_repo",
    # keep sorted
    artifacts = [
        "@maven//:androidx.activity.activity_1.0.0",
        "@maven//:androidx.appcompat.appcompat_1.1.0",
        "@maven//:androidx.lifecycle.lifecycle-common_2.1.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.1.0",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.1.0",
        "@maven//:androidx.navigation.navigation-safe-args-gradle-plugin_2.3.1",
        "@maven//:androidx.navigation.navigation-safe-args-gradle-plugin_2.5.2",
        "@maven//:androidx.savedstate.savedstate_1.0.0",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:junit.junit_4.12",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.hamcrest.hamcrest-library_1.3",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
    ],
)
