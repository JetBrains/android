load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.deploy",
    srcs = ["src"],
    iml_files = ["intellij.android.deploy.iml"],
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
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
    ],
)
