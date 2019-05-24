load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.buildCommon",
    srcs = ["src"],
    iml_files = ["intellij.android.buildCommon.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    test_coverage = True,
    test_resources = ["testResources"],
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
    exports = [
        "//tools/adt/idea/android/lib:jarutils",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/device_validator:studio.android.sdktools.dvlib",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
        "//tools/base/ninepatch:studio.android.sdktools.ninepatch",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
    ],
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
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/adt/idea/android/lib:jarutils",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/device_validator:studio.android.sdktools.dvlib[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/base/ninepatch:studio.android.sdktools.ninepatch[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
    ],
)
