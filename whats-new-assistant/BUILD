load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "whats-new-assistant",
    srcs = ["src"],
    iml_files = ["whats-new-assistant.iml"],
    resources = ["resources"],
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
        "//tools/adt/idea/assistant[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "whats-new-assistant-test",
    iml_files = ["whats-new-assistant-test.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.whatsnew.assistant.WhatsNewAssistantTestSuite",
    test_coverage = True,
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-for-studio",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/base/templates",
        "//tools/idea:platform-build-info",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["slow"],
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
        "//tools/adt/idea/assistant[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/whats-new-assistant[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
    ],
)
