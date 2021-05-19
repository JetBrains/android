load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.testutils",
    iml_files = ["intellij.android.adt.testutils.iml"],
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
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
    ],
    test_srcs = ["src/main/java"],
    test_tags = ["manual"],  # src/main/java is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/repository:studio.android.sdktools.repository[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Gradle[test]",
        "//tools/idea/.idea/libraries:jna[test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.adt.testutils",
    iml_files = ["intellij.android.adt.testutils.iml"],
    project = "unb",
    test_srcs = ["src/main/java"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/repository:studio.android.sdktools.repository[module, test]",
    ],
)
