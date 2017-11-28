load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-testutils",
    srcs = ["src/main/java"],
    iml_files = ["adt-testutils.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/base/testutils:studio.testutils[module]",
        "//tools/base/repository:studio.repository[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:jna",
    ],
)
