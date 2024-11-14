load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
    "PYTHON_PACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load(
    "//tools/adt/idea/aswb/testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)
load("//tools/adt/idea/studio:studio.bzl", "intellij_plugin_import")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

java_library(
    name = "python",
    srcs = glob(["src/**/*.java"]),
    visibility = PYTHON_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/util:transactions",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/sdkcompat",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/third_party/java/jetbrains/python",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "com.intellij.modules.python",
    plugin_xml = "src/META-INF/python-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    plugin_xmls = ["src/META-INF/blaze-python.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":python"],
)

stamped_plugin_xml(
    name = "python_plugin_xml",
    testonly = 1,
    plugin_id = "com.google.idea.blaze.python",
    plugin_name = "com.google.idea.blaze.python",
)

intellij_plugin(
    name = "python_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":python_plugin_xml",
    deps = [
        ":plugin_library",
        "//tools/adt/idea/aswb/base:plugin_library",
    ],
)

jvm_import(
    name = "python_plugin_import",
    testonly = 1,
    jars = [
        ":python_integration_test_plugin",
    ],
)

intellij_plugin_import(
    name = "com.google.idea.blaze.python",
    testonly = 1,
    overwrite_plugin_version = True,
    target_dir = "python",
    exports = [":python_plugin_import"],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    plugins = [":com.google.idea.blaze.python"],
    required_plugins = "com.google.idea.blaze.python",
    tags = [
        "noci:studio-win",
    ],
    test_package_root = "com.google.idea.blaze.python",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":integration_test_utils",
        ":python",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "//tools/vendor/google/aswb/third_party/java/jetbrains/python:python_for_tests",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "@maven//:com.google.guava.guava",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.python",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":python",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "//tools/vendor/google/aswb/third_party/java/jetbrains/python:python_for_tests",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/integration/**/*.java"]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "//tools/vendor/google/aswb/third_party/java/jetbrains/python:python_for_tests",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "@maven//:com.google.guava.guava",
    ],
)
