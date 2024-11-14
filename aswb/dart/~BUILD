load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "intellij_plugin_library")
load("//tools/adt/idea/aswb/testing:test_defs.bzl", "intellij_unit_test_suite")

java_library(
    name = "dart",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/blaze-dart.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":dart"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.dart",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":dart",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "@maven//:com.google.guava.guava",
    ],
)
