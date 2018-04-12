load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "apkanalyzer",
    srcs = ["src"],
    iml_files = ["apkanalyzer.iml"],
    tags = ["managed"],
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/analytics-library/protos:analytics-protos[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/idea/.idea/libraries:dexlib2",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/adt/idea/project-system[module]",
        "//tools/adt/idea/smali:intellij.android.smali[module]",
        "//tools/adt/idea/android-lang[module]",
    ],
)
