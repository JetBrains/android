load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

# Export testData traces
filegroup(
    name = "test_data",
    srcs = glob(["testData/**"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.profilers",
    srcs = ["src"],
    bundled_deps = ["//tools/adt/idea/profilers:unb.traceprocessor-proto"],
    iml_files = ["intellij.android.profilers.iml"],
    lint_baseline = "lint_baseline.xml",
    project = "unb",
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
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/profilers-atrace:intellij.android.profilers.atrace[module]",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase[module, test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto",
        "//tools/adt/idea/.idea/libraries:unb.perfetto-proto",
        "//tools/adt/idea/profilers:unb.traceprocessor-proto",
        "//tools/adt/idea/.idea/libraries:unb.transport-proto",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
    ],
)

kotlin_test(
    name = "intellij.android.profilers.performance",
    srcs = glob([
        "testSrc/com/android/tools/profilers/performance/**/*.kt",
        "testSrc/com/android/tools/profilers/performance/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=intellij.android.profilers.performance.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":intellij.android.profilers_testlib",
    ],
    deps = [
        ":intellij.android.profilers",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/transport/proto:transport_java_proto",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)

# managed by go/iml_to_build [unb]
java_import(
    name = "unb.traceprocessor-proto",
    jars = ["//tools/base/profiler/native/trace_processor_daemon:traceprocessor_protos.jar"],
    tags = [
        "manual",
        "unb",
    ],
    visibility = ["//visibility:public"],
)
