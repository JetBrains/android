load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers",
    srcs = ["src"],
    exclude = ["testSrc/com/android/tools/profilers/performance/DataSeriesPerformanceTest.kt"],
    iml_files = ["intellij.android.profilers.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = [
        "no_test_mac_psq",
        "no_test_windows",
    ],  # b/113099009
    test_coverage = True,
    test_data = glob(["testData/**"]) + ["//prebuilts/tools:simpleperf"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/ide-customization:intellij.java.ide.customization",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/adt/idea/profilers-atrace:intellij.android.profilers.atrace[module]",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase[module, test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/idea/.idea/libraries:studio-grpc",
        "//tools/idea/.idea/libraries:studio-proto",
        "//tools/idea/.idea/libraries:perfetto-proto",
        "//tools/idea/.idea/libraries:transport-proto",
    ],
)

kotlin_test(
    name = "intellij.android.profilers.performance",
    srcs = ["testSrc/com/android/tools/profilers/performance"],
    jvm_flags = ["-Dtest.suite.jar=intellij.android.profilers.performance.jar"],
    tags = [
        "perfgate_multi_run",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":intellij.android.profilers_testlib",
    ],
    deps = [
        ":intellij.android.profilers",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/transport/proto:transport_java_proto",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:studio-grpc",
        "//tools/idea/.idea/libraries:studio-proto",
    ],
)
