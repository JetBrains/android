#
# Description: Blaze plugin for various IntelliJ products.
#
load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "BAZEL_PLUGIN_SUBPACKAGES",
    "DEFAULT_TEST_VISIBILITY",
    "create_plugin_packages_group",
)
load("//tools/adt/idea/aswb/build_defs:aswb_module.bzl", "aswb_module")

licenses(["notice"])

create_plugin_packages_group()

# Changelog file
filegroup(
    name = "changelog",
    srcs = ["CHANGELOG"],
    visibility = BAZEL_PLUGIN_SUBPACKAGES,
)

# ASwB tests, run with an Android Studio plugin SDK
test_suite(
    name = "aswb_tests",
    tests = [
        "//tools/adt/idea/aswb/aswb:unit_tests",
        "//tools/adt/idea/aswb/base:integration_tests",
        "//tools/adt/idea/aswb/base:unit_tests",
        "//tools/adt/idea/aswb/cpp:integration_tests",
        "//tools/adt/idea/aswb/cpp:unit_tests",
        "//tools/adt/idea/aswb/dart:unit_tests",
        "//tools/adt/idea/aswb/java:integration_tests",
        "//tools/adt/idea/aswb/java:unit_tests",
    ],
    visibility = DEFAULT_TEST_VISIBILITY,
)

aswb_module(
    name = "aswb_core_module",
    aswb_module_deps = ["//tools/vendor/google/aswb/plugin_api:plugin_api_module"],
    scope_prefixes = ["//tools/adt/idea/aswb/"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/aswb/aswb",
        "//tools/adt/idea/aswb/aswb/src/com/android/tools/idea/rendering/tokens",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/cpp",
        "//tools/adt/idea/aswb/dart",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/kotlin",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/skylark",
        "//tools/adt/idea/aswb/terminal",
        "//tools/adt/idea/aswb/vcs",
    ],
)
