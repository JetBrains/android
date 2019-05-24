load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.transportDatabase",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.transportDatabase.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_coverage = True,
    test_resources = ["testData"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/usageView:intellij.platform.usageView",
        "//tools/idea/RegExpSupport:intellij.regexp",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:sqlite",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
    ],
)
