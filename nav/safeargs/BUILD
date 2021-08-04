load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.safeargs",
    srcs = ["src"],
    iml_files = ["intellij.android.nav.safeargs.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
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
        },
        "non-gradle": {},
    },
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.nav.safeargs.SafeArgsTestSuite",
    # keep sorted
    test_data = [
        ":testdeps_repo",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/nav/safeargs/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
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
    ],
)

maven_repository(
    # Dependencies needed for on-disk test projects, e.g. Gradle tests
    name = "testdeps_repo",
    # keep sorted
    artifacts = [
        "@maven_tests//:androidx.activity.activity_1.0.0",
        "@maven_tests//:androidx.appcompat.appcompat_1.1.0",
        "@maven_tests//:androidx.lifecycle.lifecycle-common_2.1.0",
        "@maven_tests//:androidx.lifecycle.lifecycle-runtime_2.1.0",
        "@maven_tests//:androidx.lifecycle.lifecycle-viewmodel_2.1.0",
        "@maven_tests//:androidx.navigation.navigation-safe-args-gradle-plugin_2.3.1",
        "@maven_tests//:androidx.savedstate.savedstate_1.0.0",
        "@maven_tests//:com.google.auto.value.auto-value_1.6.2",
        "@maven_tests//:commons-lang.commons-lang_2.4",
        "@maven_tests//:junit.junit_4.12",
        "@maven_tests//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven_tests//:org.hamcrest.hamcrest-library_1.3",
        "@maven_tests//:org.jetbrains.kotlin.kotlin-stdlib-common_1.4.32",
        "@maven_tests//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
    ],
)
