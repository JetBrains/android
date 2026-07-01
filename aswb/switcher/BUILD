load("//tools/adt/idea/aswb:build-visibility.bzl", "PLUGIN_PACKAGES_VISIBILITY")
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library", "intellij_plugin_library")
load(
    "//tools/adt/idea/aswb/testing:test_defs.bzl",
    "intellij_integration_test_suite",
)

aswb_library(
    name = "switcher_lib",
    srcs = glob([
        "src/**/*.kt",
    ]),
    visibility = [
        "//tools/adt/idea/aswb:__subpackages__",
        "//tools/vendor/google3/aswb:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/plugin_api",
    ],
)

intellij_plugin_library(
    name = "switcher",
    plugin_xmls = ["src/META-INF/switcher.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":switcher_lib",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob([
        "tests/integrationtests/**/*.java",
        "tests/integrationtests/**/*.kt",
    ]),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.switcher",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":switcher_lib",
        "//tools/adt/idea/aswb/base:base_lib",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:test_libs",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)
