load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "wizard",
    srcs = ["src"],
    iml_files = ["wizard.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # no tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
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
        "//tools/adt/idea/adt-branding",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/wizard-model[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/idea/platform/platform-impl[module, test]",
        "//tools/adt/idea/observable-ui[module]",
    ],
)
