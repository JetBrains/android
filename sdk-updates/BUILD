load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sdkUpdates",
    srcs = ["src"],
    iml_files = ["intellij.android.sdkUpdates.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui[module]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.sdkUpdates.tests",
    iml_files = ["intellij.android.sdkUpdates.tests.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.updater.SdkUpdaterTestSuite",
    test_coverage = True,
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/base/templates",
        "//tools/idea:platform-build-info",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["slow"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/base/repository:studio.android.sdktools.repository[module, test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
    ],
)
