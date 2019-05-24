load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.deploy",
    srcs = ["src"],
    iml_files = ["intellij.android.deploy.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_coverage = True,
    test_data = glob(["testData/**"]) + ["//prebuilts/studio/sdk:platform-tools"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[module]",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/base/tracer:studio.android.sdktools.tracer[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/adt/idea/build-common:intellij.android.buildCommon[module]",
    ],
)
