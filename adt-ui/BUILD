load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-ui",
    srcs = ["src/main/java"],
    iml_files = ["adt-ui.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_class = "com.android.tools.adtui.AdtUiTestSuite",
    test_resources = ["src/test/resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/adt-branding",
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/adt/idea/observable[module]",
        "//tools/adt/idea/adt-ui/lib:libwebp",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)

java_binary(
    name = "baseline-images-generator",
    main_class = "com.android.tools.adtui.imagediff.BaselineImagesGenerator",
    runtime_deps = [":adt-ui_testlib"],
)
