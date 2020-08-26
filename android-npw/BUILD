load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.newProjectWizard.tests",
    iml_files = ["intellij.android.newProjectWizard.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//prebuilts/tools/common/m2/repository/com/jetbrains/intellij/documentation/tips-intellij-idea-community/202.13:jar",
        "//tools/idea/.idea/libraries:precompiled_jshell-frontend",
        "//tools/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[module, test]",
        "//tools/idea/.idea/libraries:kotlin-plugin[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.newProjectWizard",
    srcs = ["src"],
    iml_files = ["intellij.android.newProjectWizard.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
        "//tools/idea/java/plugin:intellij.java.plugin",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//prebuilts/tools/common/m2/repository/com/jetbrains/intellij/documentation/tips-intellij-idea-community/202.13:jar",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/.idea/libraries:precompiled_jshell-frontend",
        "//tools/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-plugin",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.newProjectWizard",
    srcs = ["src"],
    iml_files = ["intellij.android.newProjectWizard.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.newProjectWizard.tests",
    iml_files = ["intellij.android.newProjectWizard.tests.iml"],
    project = "unb",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
    ],
)
