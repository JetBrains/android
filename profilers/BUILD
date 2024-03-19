load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

# Export testData traces
filegroup(
    name = "test_data",
    srcs = glob(["testData/**"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers",
    srcs = ["src"],
    iml_files = ["intellij.android.profilers.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.profilers.ProfilersTestSuite",
    test_data = [
        ":test_data",
        "//prebuilts/tools:simpleperf",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/profilers-atrace:intellij.android.profilers.atrace[module]",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase[module, test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:perfetto-proto",
        "//tools/adt/idea/profilers:traceprocessor-proto",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api[module]",
        "//tools/adt/idea/codenavigation[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
    ],
)

kotlin_test(
    name = "intellij.android.profilers.performance",
    srcs = glob([
        "testSrc/com/android/tools/profilers/performance/**/*.kt",
        "testSrc/com/android/tools/profilers/performance/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=intellij.android.profilers.performance.jar"],
    lint_baseline = "lint_baseline_performance.xml",
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":intellij.android.profilers",
        ":intellij.android.profilers_testlib",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/transport/proto:transport_java_proto",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "traceprocessor-proto",
    jars = ["//tools/base/profiler/native/trace_processor_daemon:traceprocessor_protos.jar"],
    visibility = ["//visibility:public"],
)
