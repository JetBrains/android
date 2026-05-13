load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "aswb_library",
    "intellij_plugin_library",
    "optional_plugin_xml",
)
load(
    "//tools/adt/idea/aswb/testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

aswb_library(
    name = "cpp_lib",
    srcs = glob([
        "src/com/google/idea/bazel/cpp/*.java",
        "src/com/google/idea/bazel/cpp/includes/*.java",
        "src/com/google/idea/bazel/cpp/qsync/*.java",
        "src/com/google/idea/bazel/cpp/syncstatus/*.java",
        "src/com/google/idea/bazel/cpp/navigation/*.java",
    ]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/util:platform",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
    ],
)

optional_plugin_xml(
    name = "optional_cidr",
    module = "com.intellij.cidr.lang",
    plugin_xml = "src/META-INF/blaze-cpp.xml",
)

intellij_plugin_library(
    name = "cpp",
    optional_plugin_xmls = [":optional_cidr"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":cpp_lib"],
)

java_library(
    name = "clion_sysprop_rule",
    testonly = 1,
    srcs = ["tests/utils/com/google/idea/bazel/cpp/ClionUnitTestSystemPropertiesRule.java"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",
        "//tools/adt/idea/aswb/third_party/java/junit",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    class_rules = [],
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.bazel.cpp",
    visibility = DEFAULT_TEST_VISIBILITY,
    # Needed to work around one-version issue
    runtime_deps = ["//tools/adt/idea/aswb/plugin_api:test_libs"],
    deps = [
        ":cpp_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:test_libs",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = [
        "tests/integrationtests/com/google/idea/bazel/cpp/BlazeCppIntegrationTestCase.java",
    ],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:test_libs",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(
        ["tests/integrationtests/**/*.java"],
        exclude = [
            "tests/integrationtests/com/google/idea/bazel/cpp/BlazeCppIntegrationTestCase.java",
            "tests/integrationtests/com/google/idea/bazel/cpp/BlazeCppResolvingTestCase.java",
        ],
    ),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.bazel.cpp",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":cpp_lib",
        ":integration_test_utils",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",
        "//tools/adt/idea/aswb/plugin_api:test_libs",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)
