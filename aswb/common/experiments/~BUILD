load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
    "DEFAULT_TEST_VISIBILITY",
    "G3PLUGINS_VISIBILITY",
    "SERVICES_EXPERIMENT_SUBPACKAGES",
)
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "intellij_plugin_library")
load(
    "//tools/adt/idea/aswb/testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

licenses(["notice"])

java_library(
    name = "experiments",
    srcs = glob(["src/**/*.java"]),
    resources = [":experiment_properties"],
    visibility = COMMON_PLUGINS_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/common/util:platform",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

filegroup(
    name = "experiment_properties",
    srcs = ["src/com/google/idea/common/experiments/experiment.properties"],
    visibility = SERVICES_EXPERIMENT_SUBPACKAGES,
)

# includes a test ExperimentService registered in plugin XML. This is required
# for tests of code hit during test fixture setup (before the test code itself
# can register services / components)
intellij_plugin_library(
    name = "mock_experiment_service",
    testonly = 1,
    plugin_xmls = ["tests/utils/mock_experiment_service.xml"],
    visibility = G3PLUGINS_VISIBILITY,
    deps = [":unit_test_utils"],
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/**/*.java"]),
    visibility = COMMON_PLUGINS_VISIBILITY,
    deps = [
        ":experiments",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.common.experiments",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":experiments",
        ":unit_test_utils",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)
