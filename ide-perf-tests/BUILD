load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.ide-perf-tests",
    iml_files = ["intellij.android.ide-perf-tests.iml"],
    split_test_targets = {
        "SantaTrackerBenchmark": {
            "test_filter": "com.android.tools.idea.benchmarks.SantaTrackerBenchmark",
        },
        "SantaTrackerKotlinBenchmark": {
            "test_filter": "com.android.tools.idea.benchmarks.SantaTrackerKotlinBenchmark",
        },
        "other": {},
    },
    test_class = "com.android.tools.idea.benchmarks.IdeBenchmarkTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/buildbenchmarks:SantaTrackerKotlin_project",
        "//prebuilts/studio/buildbenchmarks:SantaTracker_project",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/mlkit/testData",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/ide-perf-tests/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
        "perfgate",
    ],
    test_timeout = "eternal",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)
