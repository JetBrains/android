load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
    "DEFAULT_TEST_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

licenses(["notice"])

java_library(
    name = "properties",
    srcs = glob(["src/**/*.java"]),
    visibility = COMMON_PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.common.ui.properties",
    visibility = DEFAULT_TEST_VISIBILITY,
    runtime_deps = [
        "//tools/vendor/google/aswb/plugin_api:guava_for_external_binaries",
    ],
    deps = [
        ":properties",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "//tools/vendor/google/aswb/third_party/java/junit",
    ],
)
