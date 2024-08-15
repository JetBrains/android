#
# Description: Blaze plugin for various IntelliJ products.
#
load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "BAZEL_PLUGIN_SUBPACKAGES",
    "DEFAULT_TEST_VISIBILITY",
    "create_plugin_packages_group",
)

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
        "//tools/adt/idea/aswb/aswb:integration_tests",
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
