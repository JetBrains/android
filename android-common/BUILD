load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.common",
    srcs = ["src"],
    iml_files = ["intellij.android.common.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.AndroidCommonTestSuite",
    test_coverage = True,
    test_data = [
        "//tools/adt/idea/adt-ui/lib/libwebp",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/idea:platform-build-info",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
    ],
    test_srcs = ["test"],
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
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module, test]",
        "//tools/base/ninepatch:studio.android.sdktools.ninepatch[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)
