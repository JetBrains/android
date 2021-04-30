load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

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
        "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
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
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/sync-perf-tests:JUnit4[test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
    ],
)

maven_repo(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "//prebuilts/tools/common/m2/repository/android/arch/core/common/1.1.1:jar",
        "//prebuilts/tools/common/m2/repository/android/arch/core/runtime/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/common/1.1.1:jar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/livedata-core/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/livedata/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/runtime/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/viewmodel/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/animated-vector-drawable/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/appcompat-v7/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/asynclayoutinflater/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/collections/28.0.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/constraint/constraint-layout-solver/1.0.2:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/constraint/constraint-layout/1.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/coordinatorlayout/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/cursoradapter/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/customview/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/documentfile/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/drawerlayout/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/interpolator/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/loader/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/localbroadcastmanager/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/print/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/slidingpanelayout/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-annotations/28.0.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-compat/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-core-ui/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-core-utils/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-fragment/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-vector-drawable/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/swiperefreshlayout/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/espresso/espresso-core/3.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/espresso/espresso-idling-resource/3.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/monitor/1.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/runner/1.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/uiautomator/uiautomator-v18/2.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/versionedparcelable/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/viewpager/28.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/facebook/testing/screenshot/core/0.2.3:jar",
        "//prebuilts/tools/common/m2/repository/com/google/android/android/4.1.1.4:jar",
        "//prebuilts/tools/common/m2/repository/com/google/auto/value/auto-value/1.4-rc1:jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/findbugs/jsr305/2.0.1:jar",
        "//prebuilts/tools/common/m2/repository/com/google/dexmaker/dexmaker-mockito/1.2:jar",
        "//prebuilts/tools/common/m2/repository/com/google/dexmaker/dexmaker/1.2:jar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/19.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/testing/compile/compile-testing/0.9:jar",
        "//prebuilts/tools/common/m2/repository/com/jakewharton/auto/value/auto-value-annotations/1.2-update1:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/javawriter/2.1.1:jar",
        "//prebuilts/tools/common/m2/repository/io/opentracing/opentracing-api/0.13.0:jar",
        "//prebuilts/tools/common/m2/repository/io/opentracing/opentracing-impl/0.13.0:jar",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-integration/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-core/1.10.19:jar",
    ],
    use_zip = False,
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "JUnit4",
    # do not sort: must match IML order
    jars = [
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:JUnit4_files",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:JUnit4_files",
    ],
    visibility = ["//visibility:public"],
)
