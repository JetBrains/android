load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lang",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
        "resources",
    ],
    iml_files = ["intellij.android.lang.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = ["no_test_mac_psq"],
    test_runtime_deps = ["//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/idea:intellij.java.analysis.impl[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/uast/uast-common:intellij.platform.uast[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lang.tests",
    iml_files = ["intellij.android.lang.tests.iml"],
    tags = [
        "no_test_mac_psq",
        "no_test_windows",
    ],  # b/113099009
    test_class = "com.android.tools.idea.lang.AndroidLangTestSuite",
    test_coverage = True,
    test_data = glob(["testData/**"]) + [
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-for-studio",
        "//tools/adt/idea/android/annotations",
        "//tools/base/templates",
        "//tools/idea:platform-build-info",
        "//tools/idea/java/mockJDK-1.7",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["slow"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/plugins/IntelliLang:intellij.xml.langInjection",
        "//tools/idea/uast/uast-java:intellij.java.uast",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-lang:intellij.android.lang[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
    ],
)
