load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "ASWB_SUBPACKAGES_VISIBILITY",
    "KOTLIN_PACKAGE_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "combine_visibilities",
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

licenses(["notice"])

java_library(
    name = "kotlin",
    srcs = glob(["src/**/*.java"]),
    visibility = combine_visibilities(ASWB_SUBPACKAGES_VISIBILITY, KOTLIN_PACKAGE_VISIBILITY),
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep
        "//tools/vendor/google/aswb/plugin_api:kotlin",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.jetbrains.kotlin",
    plugin_xml = "src/META-INF/kotlin-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    plugin_xmls = ["src/META-INF/blaze-kotlin.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":kotlin"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze",
    deps = [
        ":kotlin",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:kotlin_for_tests",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)

stamped_plugin_xml(
    name = "kotlin_plugin_xml",
    testonly = 1,
    plugin_id = "com.google.idea.blaze.kotlin",
    plugin_name = "com.google.idea.blaze.kotlin",
)

intellij_plugin(
    name = "kotlin_integration_test_plugin",
    testonly = 1,
    optional_plugin_xmls = [":optional_xml"],
    plugin_xml = ":kotlin_plugin_xml",
    deps = [
        ":plugin_library",
        "//tools/adt/idea/aswb/base:plugin_library",
        "//tools/adt/idea/aswb/java:plugin_library",
    ],
)

jvm_import(
    name = "kotlin_plugin_import",
    testonly = 1,
    jars = [
        ":kotlin_integration_test_plugin",
    ],
)

intellij_plugin_import(
    name = "com.google.idea.blaze.kotlin",
    testonly = 1,
    overwrite_plugin_version = True,
    target_dir = "bazelkotlin",
    exports = [":kotlin_plugin_import"],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    plugins = [
        ":com.google.idea.blaze.kotlin",
        "//tools/adt/idea/studio:org.jetbrains.android",
    ],
    required_plugins = "org.jetbrains.kotlin,com.google.idea.blaze.kotlin",
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.kotlin",
    deps = [
        ":kotlin",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:kotlin_for_tests",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)
