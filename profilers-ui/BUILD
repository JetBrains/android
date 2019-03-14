load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.profilers.ui.iml"],
    test_coverage = True,
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/usageView:intellij.platform.usageView",
        "//tools/idea/RegExpSupport:intellij.regexp",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/profilers:intellij.android.profilers[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/.idea/libraries:Java_Compatibility",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
    ],
)

kotlin_test(
    name = "intellij.android.profilers.ui.performance",
    srcs = ["testSrc/com/android/tools/profilers/performance"],
    data = glob(["testData/**"]),
    jvm_flags = ["-Dtest.suite.jar=intellij.android.profilers.ui.performance.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":intellij.android.profilers.ui_testlib",
    ],
    deps = [
        ":intellij.android.profilers.ui",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/profilers:intellij.android.profilers",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:truth",
        "//tools/idea/platform/core-api:intellij.platform.core",
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt",
    ],
)
