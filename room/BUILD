load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.room",
    srcs = ["src"],
    iml_files = ["intellij.android.room.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/groovy/jps-plugin:intellij.groovy.jps",
        "//tools/idea/plugins/ant/jps-plugin:intellij.ant.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/plugins/properties:intellij.properties",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea/plugins/testng:intellij.testng",
        "//tools/idea/plugins/junit:intellij.junit",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/coverage:intellij.java.coverage",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/ByteCodeViewer:intellij.java.byteCodeViewer",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/ide-customization:intellij.java.ide.customization",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/adt/idea/android-lang:intellij.android.lang[module]",
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.room.tests",
    iml_files = ["intellij.android.room.tests.iml"],
    tags = [
        "no_test_windows",  # b/135665870
    ],
    test_class = "com.android.tools.idea.room.AndroidRoomTestSuite",
    test_coverage = True,
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/idea:platform-build-info",
        "//tools/idea/java/jdkAnnotations",
        "//tools/idea/java/mockJDK-1.7",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/ide-customization:intellij.java.ide.customization",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
        "//tools/idea/java/structuralsearch-java:intellij.java.structuralSearch",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/room:intellij.android.room[module]",
        "//tools/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
