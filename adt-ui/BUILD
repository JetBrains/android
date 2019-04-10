load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.ui",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.adt.ui.iml"],
    resources = ["resources"],
    test_class = "com.android.tools.adtui.AdtUiTestSuite",
    test_coverage = True,
    test_data = [
        "//tools/idea:platform-build-info",
    ],
    test_resources = ["src/test/resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
    ],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
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
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/adt-ui/lib:libwebp",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:Java_Compatibility",
    ],
)

java_binary(
    name = "baseline-images-generator",
    testonly = True,
    main_class = "com.android.tools.adtui.imagediff.BaselineImagesGenerator",
    runtime_deps = [":intellij.android.adt.ui_testlib"],
)
