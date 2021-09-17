load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

SYNC_PERFGATE_TAGS = [
    "block-network",
    "no_test_mac",
    "no_test_windows",
    "perfgate",
]

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sync-perf-tests",
    iml_files = ["intellij.android.sync-perf-tests.iml"],
    split_test_targets = {
        # Smoke tests
        "AverageSmoke": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.AverageSmokeTest",
            "timeout": "long",
            "tags": [
                "no_test_mac",
                "no_test_windows",
            ],
        },
        "Base100Smoke": {
            "data": ["//prebuilts/studio/buildbenchmarks:Base100_project"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.Base100SmokeTest",
            "timeout": "long",
            "tags": [
                "no_test_mac",
                "no_test_windows",
            ],
        },
        "Base100KotlinSmoke": {
            "data": ["//prebuilts/studio/buildbenchmarks:Base100_project"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.Base100KotlinSmokeTest",
            "timeout": "long",
            "tags": [
                "no_test_mac",
                "no_test_windows",
            ],
        },
        "DolphinSmoke": {
            "data": ["//prebuilts/studio/buildbenchmarks/dolphin.3627ef8a"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.DolphinSmokeTest",
            "timeout": "long",
            "tags": [
                "no_test_mac",
                "no_test_windows",
            ],
        },
        "SantaTrackerSmoke": {
            "data": ["//prebuilts/studio/buildbenchmarks:SantaTracker_project"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.SantaTrackerSmokeTest",
            "timeout": "long",
            "tags": [
                "no_test_mac",
                "no_test_windows",
            ],
        },
        # Perfgate tests
        "Base100": {
            "data": ["//prebuilts/studio/buildbenchmarks:Base100_project"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.Base100PerfTest",
            "tags": SYNC_PERFGATE_TAGS,
        },
        "Base100Kotlin": {
            "data": ["//prebuilts/studio/buildbenchmarks:Base100_project"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.Base100KotlinPerfTest",
            "tags": SYNC_PERFGATE_TAGS,
        },
        "Dolphin": {
            "data": ["//prebuilts/studio/buildbenchmarks/dolphin.3627ef8a"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.DolphinPerfTest",
            "tags": SYNC_PERFGATE_TAGS,
        },
        "ExtraLarge": {
            "data": ["//prebuilts/studio/buildbenchmarks:ExtraLarge_project"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.ExtraLargePerfTest",
            # Only run manually since this project requires too much memory (b/176172752)
            "timeout": "long",
            "tags": ["manual"],
        },
        "SantaTracker": {
            "data": ["//prebuilts/studio/buildbenchmarks:SantaTracker_project"],
            "test_filter": "com.android.tools.idea.gradle.project.sync.perf.SantaTrackerPerfTest",
            "tags": SYNC_PERFGATE_TAGS,
        },
        # This split test target contains AveragePerfTest, and other unspecified tests if
        # they exist. Additional targets should be made if this one begins taking a long time.
        "other": {
            "tags": SYNC_PERFGATE_TAGS,
        },
    },
    test_class = "com.android.tools.idea.SyncPerfTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:platforms/latest-preview",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/sync-perf-tests/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
    ],
    test_timeout = "eternal",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//prebuilts/tools/common/m2:JUnit4[test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.android.support.test.uiautomator.uiautomator-v18_2.1.1",
        "@maven//:com.facebook.testing.screenshot.core_0.2.3",
        "@maven//:com.google.android.android_4.1.1.4",
        "@maven//:com.google.auto.value.auto-value_1.4-rc1",
        "@maven//:com.google.dexmaker.dexmaker-mockito_1.2",
        "@maven//:com.google.guava.guava_19.0",
        "@maven//:com.jakewharton.auto.value.auto-value-annotations_1.2-update1",
        "@maven//:io.opentracing.opentracing-impl_0.13.0",
    ],
    visibility = ["//visibility:public"],
)
