load("@exec_properties//:constants.bzl", "LARGE_MACHINE")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sync-memory-tests",
    srcs = ["src"],
    iml_files = ["intellij.android.sync-memory-tests.iml"],
    split_test_targets = {
        "Benchmark2000Memory": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.Benchmark2000MemoryTest",
            "exec_properties": LARGE_MACHINE,
        },
        "Benchmark1000Cpu": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.cpu.Benchmark1000CpuTest",
        },
        "Benchmark2000Cpu": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.cpu.Benchmark2000CpuTest",
            "exec_properties": LARGE_MACHINE,
            "additional_jvm_args": ["-Xmx6g"],
        },
        # Tests without regression analyzers, to reduce noise
        "Benchmark200Cpu": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.cpu.Benchmark200CpuTest",
        },
        "Benchmark500Cpu": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.cpu.Benchmark500CpuTest",
        },
        "Benchmark200Repeated20TimesMemory": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.Benchmark200Repeated20TimesMemoryTest",
        },
        "Benchmark1000Memory": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.Benchmark1000MemoryTest",
        },
        "Benchmark1000MemoryRuntimeClasspath": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.Benchmark1000MemoryRuntimeClasspath",
        },
        "Benchmark1000CpuRuntimeClasspath": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.cpu.Benchmark1000CpuRuntimeClasspath",
        },
        # Disabled tests
        "Benchmark4200Cpu": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.cpu.Benchmark4200CpuTest",
            "exec_properties": LARGE_MACHINE,
            "tags": ["manual"],
        },
        "BenchmarkMultiApp100Cpu": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.cpu.BenchmarkMultiApp100CpuTest",
            "tags": ["manual"],
        },
        "BenchmarkMultiApp190Cpu": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.cpu.BenchmarkMultiApp190CpuTest",
            "exec_properties": LARGE_MACHINE,
            "additional_jvm_args": ["-Xmx6g"],
            "tags": ["manual"],
        },
        "Benchmark4200Memory": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.Benchmark4200MemoryTest",
            "exec_properties": LARGE_MACHINE,
            "tags": ["manual"],
        },
        "BenchmarkMultiApp100Memory": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.BenchmarkMultiApp100MemoryTest",
            "tags": ["manual"],
        },
        "BenchmarkMultiApp190Memory": {
            "test_filter": "com.android.tools.idea.gradle.project.sync.memory.BenchmarkMultiApp190MemoryTest",
            "tags": ["manual"],
        },
    },
    test_data = [
        "testSrc/com/android/tools/idea/gradle/project/sync/gradle/MeasurementPlugin.kt",
        "//prebuilts/studio/buildbenchmarks:ExtraLarge2022_project",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-32",  # Used for model v1 tests that needs AGP 7.1 (which needs compile sdk 32)
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/eclipse-mat",
        "//tools/adt/idea/memory-usage/src/main/native:liblightweight_heap_traverse",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
        "//tools/data-binding:data_binding_runtime.zip",
    ],
    test_jvm_flags = [
        "-agentpath:$(location //tools/adt/idea/memory-usage/src/main/native:liblightweight_heap_traverse)",
        "-Dmemory.agent.path=$(location //tools/adt/idea/memory-usage/src/main/native:liblightweight_heap_traverse)",
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
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/memory-usage[module, test]",
        "//tools/adt/idea/.idea/libraries:javax-inject[test]",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
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
