load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
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
    name = "cpp",
    srcs = glob([
        "src/com/google/idea/blaze/cpp/*.java",
        "src/com/google/idea/blaze/cpp/includes/*.java",
        "src/com/google/idea/blaze/cpp/qsync/*.java",
        "src/com/google/idea/blaze/cpp/syncstatus/*.java",
        "src/com/google/idea/blaze/cpp/navigation/*.java",
    ]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/util:platform",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

optional_plugin_xml(
    name = "optional_cidr",
    module = "com.intellij.modules.cidr.lang",
    plugin_xml = "src/META-INF/blaze-cpp.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_cidr"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":cpp"],
)

java_library(
    name = "clion_sysprop_rule",
    testonly = 1,
    srcs = ["tests/utils/com/google/idea/blaze/cpp/ClionUnitTestSystemPropertiesRule.java"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# exposed so plugins can optionally depend on it
java_library(
    name = "compiler_version_checker",
    srcs = ["src/com/google/idea/blaze/cpp/CompilerVersionChecker.java"],
    neverlink = 1,
    deps = ["//tools/vendor/google/aswb/plugin_api"],
)

# exposed so plugins can optionally depend on it
java_library(
    name = "compiler_wrapper_provider",
    srcs = ["src/com/google/idea/blaze/cpp/CompilerWrapperProvider.java"],
    neverlink = 1,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    class_rules = [],
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.cpp",
    visibility = DEFAULT_TEST_VISIBILITY,
    # Needed to work around one-version issue
    runtime_deps = ["//tools/vendor/google/aswb/plugin_api:test_libs"],
    deps = [
        ":cpp",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = [
        "tests/integrationtests/com/google/idea/blaze/cpp/BlazeCppIntegrationTestCase.java",
        "tests/integrationtests/com/google/idea/blaze/cpp/BlazeCppResolvingTestCase.java",
    ],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)

stamped_plugin_xml(
    name = "cpp_plugin_xml",
    testonly = 1,
    plugin_id = "com.google.idea.blaze.cpp",
    plugin_name = "com.google.idea.blaze.cpp",
)

intellij_plugin(
    name = "cpp_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":cpp_plugin_xml",
    deps = [
        ":plugin_library",
        "//tools/adt/idea/aswb/base:plugin_library",
    ],
)

jvm_import(
    name = "cpp_plugin_import",
    testonly = 1,
    jars = [
        ":cpp_integration_test_plugin",
    ],
)

intellij_plugin_import(
    name = "com.google.idea.blaze.cpp",
    testonly = 1,
    overwrite_plugin_version = True,
    target_dir = "cpp",
    exports = [":cpp_plugin_import"],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(
        ["tests/integrationtests/**/*.java"],
        exclude = [
            "tests/integrationtests/com/google/idea/blaze/cpp/BlazeCppIntegrationTestCase.java",
            "tests/integrationtests/com/google/idea/blaze/cpp/BlazeCppResolvingTestCase.java",
        ],
    ),
    plugins = [":com.google.idea.blaze.cpp"],
    required_plugins = "com.google.idea.blaze.cpp",
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.cpp",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":cpp",
        ":integration_test_utils",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)
