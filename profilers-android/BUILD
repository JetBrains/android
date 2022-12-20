load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilersAndroid",
    srcs = ["src"],
    iml_files = ["intellij.android.profilersAndroid.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.profilers.ProfilersAndroidTestSuite",
    test_data = [
        "//tools/adt/idea/profilers:test_data",
        "//prebuilts/tools/common/trace-processor-daemon",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui[module]",
        "//tools/adt/idea/profilers:intellij.android.profilers[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/native-symbolizer[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:perfetto-proto",
        "//tools/adt/idea/profilers-android:traceprocessor-proto",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/adt/idea/inspectors-common/api-ide:intellij.android.inspectors-common.api-ide[module]",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui[module]",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/codenavigation[module]",
        "//prebuilts/tools/common/m2:trace-perfetto-library",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide[module]",
    ],
)

kotlin_test(
    name = "intellij.android.profilersAndroid.performance",
    timeout = "long",
    srcs = [
        "testSrc/com/android/tools/idea/profilers/performance/CpuProfilerAtraceCaptureTest.kt",
        "testSrc/com/android/tools/idea/profilers/performance/CpuProfilerEmptyCaptureTest.kt",
        "testSrc/com/android/tools/idea/profilers/performance/CpuProfilerMemoryLoadTestBase.kt",
        "testSrc/com/android/tools/idea/profilers/performance/CpuProfilerPerfettoCaptureTest.kt",
        "testSrc/com/android/tools/idea/profilers/performance/MemoryClassifierViewFindSuperSetNodeTest.kt",
        "testSrc/com/android/tools/idea/profilers/performance/MemoryProfilerHeapDumpTest.kt",
        "testSrc/com/android/tools/idea/profilers/performance/TraceProcessorDaemonBenchmarkTest.kt",
    ],
    data = [
        "//prebuilts/tools/common/trace-processor-daemon",
        "//tools/adt/idea/profilers:test_data",
    ],
    jvm_flags = ["-Dtest.suite.jar=intellij.android.profilersAndroid.performance.jar"],
    tags = [
        "no_test_mac",  # b/168793471
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":intellij.android.profilersAndroid_testlib",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/profilers:intellij.android.profilers",
        "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/base/testutils:studio.android.sdktools.testutils",
    ],
)

kotlin_test(
    name = "intellij.android.profilersAndroid.performance.captureDetails",
    timeout = "long",
    srcs = [
        "testSrc/com/android/tools/idea/profilers/performance/CaptureDetailsTest.kt",
    ],
    data = [
        "//prebuilts/tools/common/trace-processor-daemon",
        "//tools/adt/idea/profilers:test_data",
    ],
    jvm_flags = ["-Dtest.suite.jar=intellij.android.profilersAndroid.performance.captureDetails.jar"],
    tags = [
        "no_test_mac",  # b/168793471
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":intellij.android.profilersAndroid_testlib",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/profilers:intellij.android.profilers",
        "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/base/testutils:studio.android.sdktools.testutils",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "traceprocessor-proto",
    jars = ["//tools/base/profiler/native/trace_processor_daemon:traceprocessor_protos.jar"],
    visibility = ["//visibility:public"],
)
