load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sync-perf-tests",
    iml_files = ["intellij.android.sync-perf-tests.iml"],
    test_class = "com.android.tools.idea.SyncPerfTestSuite",
    # keep sorted
    test_data = [
        ":test_deps.zip",
        "//prebuilts/studio/buildbenchmarks/dolphin.3627ef8a",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:platforms/latest-preview",
        "//prebuilts/tools/common/kotlin-plugin:Kotlin/kotlinc/build.txt",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/sync-perf-tests/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo.zip",
        "//tools/base/templates",
        "//tools/idea:platform-build-info",
        "//tools/idea/java/jdkAnnotations",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/adt/idea/databinding:intellij.android.databinding",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar[test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module, test]",
        "//tools/idea/plugins/gradle:intellij.gradle.common[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:protobuf[test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
    ],
)

maven_repo(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "//prebuilts/tools/common/m2/repository/com/android/support/constraint/constraint-layout-solver/1.0.2:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/constraint/constraint-layout/1.0.2:aar",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar",
    ],
    visibility = ["//visibility:public"],
)
