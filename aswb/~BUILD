#
# Description: Blaze plugin for various IntelliJ products.
#
load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "BAZEL_PLUGIN_SUBPACKAGES",
    "DEFAULT_TEST_VISIBILITY",
    "create_plugin_packages_group",
)
load("//tools/adt/idea/aswb/build_defs:aswb_module.bzl", "aswb_module", "aswb_plugin_module")
load("//tools/adt/idea/studio:studio.bzl", "studio_plugin")

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
        "//tools/adt/idea/aswb/java:integration_tests",
        "//tools/adt/idea/aswb/java:unit_tests",
    ],
    visibility = DEFAULT_TEST_VISIBILITY,
)

aswb_module(
    name = "aswb_core_module",
    aswb_module_deps = ["//tools/vendor/google/aswb/plugin_api:plugin_api_module"],
    scope_prefixes = ["//tools/adt/idea/aswb/"],
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/aswb/aswb",
        "//tools/adt/idea/aswb/aswb/src/com/android/tools/idea/rendering/tokens",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/cpp",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/kotlin",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/skylark",
        "//tools/adt/idea/aswb/terminal",
        "//tools/adt/idea/aswb/vcs",
    ],
)

aswb_plugin_module(
    name = "aswb_plugin_xml",
    plugin_xml = "META-INF/plugin.xml",
    visibility = ["//tools/vendor/google/aswb:__pkg__"],
    deps = [
        ":aswb_core_module",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_module",
    ],
)

studio_plugin(
    name = "com.google.idea.bazel.aswb",
    directory = "aswb",
    libs = [
        "//tools/base/annotations:annotations",
        "@@maven//:com.google.auto.value.auto-value",
        "@@maven//:com.google.auto.value.auto-value-annotations",
        "@@maven//:org.jspecify.jspecify",
        "@@maven//:com.google.errorprone.error_prone_annotations",
    ],
    modules = {
        "aswb_core.jar": [
            ":aswb_core_module",
            "//tools/vendor/google/aswb/plugin_api:plugin_api_module",
        ],
        "aswb_core_plugin_xml.jar": ["//tools/adt/idea/aswb:aswb_plugin_xml"],
    },
    resources = {
        "aspect": [
            "//tools/adt/idea/aswb/aspect:aspect_files_only",
        ],
    },
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/studio:com.android.tools.design",
        "//tools/adt/idea/studio:org.jetbrains.android",
    ],
)
