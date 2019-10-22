load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-inspection",
    srcs = ["src"],
    iml_files = ["intellij.android.app-inspection.iml"],
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
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/idea/.idea/libraries:transport-proto",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:studio-proto",
        "//tools/idea/.idea/libraries:studio-grpc",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-inspection.tests",
    iml_files = ["intellij.android.app-inspection.tests.iml"],
    test_class = "com.android.tools.idea.appinspection.AppInspectionTestSuite",
    test_coverage = True,
    test_data = [
        "//tools/idea:platform-build-info",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/ide-customization:intellij.java.ide.customization",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
        "//tools/adt/idea/app-inspection:intellij.android.app-inspection[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/idea/.idea/libraries:studio-proto[test]",
        "//tools/idea/.idea/libraries:transport-proto[test]",
        "//tools/idea/.idea/libraries:studio-grpc[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
