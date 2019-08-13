load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.testFramework",
    iml_files = ["intellij.android.testFramework.iml"],
    test_coverage = True,
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/ide-customization:intellij.java.ide.customization",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jsr305[test]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/plugins/junit:intellij.junit[module]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.impl[module]",
        "//tools/idea/plugins/gradle:intellij.gradle[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
    ],
)
