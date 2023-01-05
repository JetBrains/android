load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sync-memory-tests",
    srcs = ["src"],
    iml_files = ["intellij.android.sync-memory-tests.iml"],
    split_test_targets = {
        "Benchmark100": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.Benchmark100MemoryTest",
        },
        "Benchmark200": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.Benchmark200MemoryTest",
        },
    },
    test_class = "com.android.tools.idea.gradle.project.sync.memory.SyncMemoryTestSuite",
    test_data = [
        "//prebuilts/studio/buildbenchmarks:ExtraLarge2022_project",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-32",  # Used for model v1 tests that needs AGP 7.1 (which needs compile sdk 32)
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/eclipse-mat",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
        "//tools/data-binding:data_binding_runtime.zip",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
        "cpu:3",
        "no_test_mac",
        "no_test_windows",
        "perfgate",
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
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
    ],
)

java_binary(
    name = "benchmark_summary_tool",
    testonly = True,
    main_class = "com.android.tools.idea.gradle.project.sync.memory.BenchmarkSummaryTool",
    runtime_deps = [":benchmark_summary_lib"],
)

kotlin_library(
    name = "benchmark_summary_lib",
    srcs = ["src/com/android/tools/idea/gradle/project/sync/memory/BenchmarkSummaryTool.kt"],
    deps = ["@maven//:com.google.code.gson.gson"],
)
