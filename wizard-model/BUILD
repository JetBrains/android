load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wizard.model",
    srcs = ["src"],
    iml_files = ["intellij.android.wizard.model.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/platform/inspect:intellij.platform.inspect",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:jaxb-api",
        "//tools/idea/.idea/libraries:jaxb-runtime",
        "//tools/idea/.idea/libraries:javax.annotation-api",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.wizard.model",
    srcs = ["src"],
    iml_files = ["intellij.android.wizard.model.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
    ],
)
