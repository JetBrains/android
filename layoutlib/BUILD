load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib",
    # keep sorted
    bundle_data = [
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//tools/adt/idea/resources-aar:framework_res",
    ],
    iml_files = ["intellij.android.layoutlib.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    exports = ["//prebuilts/studio/layoutlib:data/layoutlib"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/layoutlib:data/layoutlib",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib-loader",
    srcs = ["src"],
    iml_files = ["intellij.android.layoutlib-loader.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
    ],
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
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib-loader.tests",
    iml_files = ["intellij.android.layoutlib-loader.tests.iml"],
    test_coverage = True,
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-test[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib-loader[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:ASM[test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module, test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
    ],
)
