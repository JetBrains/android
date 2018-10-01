load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers",
    srcs = ["src"],
    exclude = ["testSrc/com/android/tools/profilers/performance/DataSeriesPerformanceTest.kt"],
    iml_files = ["intellij.android.profilers.iml"],
    tags = ["no_test_mac_psq"],  # b/113099009
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/adt/idea/profilers-atrace:intellij.android.profilers.atrace[module]",
        "//tools/adt/idea/perfd-host:intellij.android.perfdHost[module, test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
    ],
)

kotlin_test(
    name = "intellij.android.profilers.performance",
    srcs = ["testSrc/com/android/tools/profilers/performance"],
    jvm_flags = ["-Dtest.suite.jar=intellij.android.profilers.performance.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":intellij.android.profilers_testlib",
    ],
    deps = [
        ":intellij.android.profilers",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/perfd-host:intellij.android.perfdHost",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/base/testutils:tools.testutils",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
    ],
)
