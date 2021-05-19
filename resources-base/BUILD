load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")
load("//tools/base/bazel:maven.bzl", "maven_java_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.resources-base",
    srcs = ["src"],
    iml_files = ["intellij.android.resources-base.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/.idea/libraries:javax.activation"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:fastutil-min",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
        "//tools/idea/.idea/libraries:aapt-proto",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:protobuf",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.resources-base.tests",
    iml_files = ["intellij.android.resources-base.tests.iml"],
    test_class = "com.android.tools.idea.resources.aar.ResourcesAarTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/res",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
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
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/adt/idea/databinding:intellij.android.databinding",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
        "block-network",
        "no_test_windows",
    ],
    test_timeout = "long",  # "medium" is not sufficient on Mac b/155999536
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/resources-base:intellij.android.resources-base[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:aapt-proto[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:protobuf[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)

java_binary(
    name = "framework-res-jar-creator",
    main_class = "com.android.tools.idea.resources.aar.FrameworkResJarCreator",
    runtime_deps = [":intellij.android.resources-base_runtime"],
)

genrule(
    name = "framework_res",
    srcs = [
        "//prebuilts/studio/layoutlib/data/res",
        "//prebuilts/studio/layoutlib/data/res:version",
    ],
    outs = ["framework_res.jar"],
    cmd = "$(JAVA) -classpath $(location :framework-res-jar-creator_deploy.jar) " + "com.android.tools.idea.resources.aar.FrameworkResJarCreator " + "$(location //prebuilts/studio/layoutlib/data/res:version)/.. $(OUTS)",
    toolchains = ["//prebuilts/studio/jdk:jdk_runtime"],
    tools = [
        ":framework-res-jar-creator",
        ":framework-res-jar-creator_deploy.jar",
    ],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.resources-base",
    srcs = ["src"],
    iml_files = ["intellij.android.resources-base.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/.idea/libraries:unb.aapt-proto",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.resources-base.tests",
    iml_files = ["intellij.android.resources-base.tests.iml"],
    project = "unb",
    test_class = "com.android.tools.idea.resources.aar.ResourcesAarTestSuite",
    test_data = [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/res",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    test_runtime_deps = ["//tools/adt/idea/databinding:intellij.android.databinding"],
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
        "block-network",
        "no_test_windows",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/resources-base:intellij.android.resources-base[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.aapt-proto[test]",
        "//tools/adt/idea/.idea/libraries:unb.protobuf[test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
    ],
)
