load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "flat_archive")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.updater.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.updater.ui.iml"],
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
        "//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/8.4.0:jar",
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
        "//tools/idea/updater:intellij.platform.updater[module]",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/adt/idea/android:intellij.android.core[module]",
    ],
)

java_binary(
    name = "patcher",
    main_class = "com.intellij.updater.Runner",
    stamp = 0,
    tags = [
        "block_network",
        "manual",
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
        ":intellij.android.updater.ui.jar",
        "//prebuilts/studio/intellij-sdk:studio-sdk-updater",
    ],
)

flat_archive(
    name = "sdk-patcher",
    ext = "zip",
    files = {
        ":source.properties": "sdk-patcher/source.properties",
        ":patcher_deploy.jar": "sdk-patcher/patcher.jar",
    },
    tags = [
        "manual",
        "no_mac",
        "no_windows",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.updater.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.updater.ui.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-updater",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
    ],
)
