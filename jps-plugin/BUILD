load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-jps-plugin",
    srcs = ["src"],
    iml_files = ["android-jps-plugin.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
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
        "//tools/adt/idea/build-common[module]",
        "//tools/adt/idea/rt:android-rt[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/plugins/maven/jps-plugin:maven-jps-plugin[module, test]",
        "//tools/base/build-system:studio.manifest-merger[module]",
        "//tools/idea/platform/testFramework[module, test]",
    ],
)
