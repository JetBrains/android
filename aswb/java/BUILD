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
    name = "java",
    srcs = glob(
        ["src/**/*.java"],
    ),
    javacopts = ["-Xep:FutureReturnValueIgnored:OFF"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/actions",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/settings",
        "//tools/adt/idea/aswb/common/util:concurrency",
        "//tools/adt/idea/aswb/common/util:platform",
        "//tools/adt/idea/aswb/common/util:transactions",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:junit",
    ],
)

optional_plugin_xml(
    name = "optional_java",
    module = "JUnit",
    plugin_xml = "src/META-INF/java-contents.xml",
)

optional_plugin_xml(
    name = "optional_coverage",
    module = "com.intellij.modules.coverage",
    plugin_xml = "src/META-INF/coverage-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [
        ":optional_java",
        ":optional_coverage",
    ],
    plugin_xmls = ["src/META-INF/blaze-java.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":java"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    # Used for the compiler tests
    data = [
        ":auto_value_lib_deploy.jar",
        ":auto_value_plugin_lib_deploy.jar",
        ":guava_lib_deploy.jar",
        ":truth_lib_deploy.jar",
    ],
    jvm_flags = [
        "-Dauto_value.jar=$(location :auto_value_lib_deploy.jar)",
        "-Dauto_value_plugin.jar=$(location :auto_value_plugin_lib_deploy.jar)",
        "-Dguava.jar=$(location :guava_lib_deploy.jar)",
        "-Dtruth.jar=$(location :truth_lib_deploy.jar)",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    ],
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.java",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":java",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:coverage_for_tests",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "//tools/vendor/google/aswb/plugin_api:truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_binary(
    name = "auto_value_lib",
    main_class = "None",
    runtime_deps = ["//tools/adt/idea/aswb/third_party/java/auto_value"],
)

java_binary(
    name = "auto_value_plugin_lib",
    main_class = "None",
    runtime_deps = ["//tools/adt/idea/aswb/third_party/java/auto_value"],
)

java_binary(
    name = "guava_lib",
    testonly = True,
    main_class = "None",
    runtime_deps = [
        "@maven//:com.google.guava.guava",
    ],
)

java_binary(
    name = "truth_lib",
    testonly = 1,
    main_class = "None",
    runtime_deps = [
        "//tools/adt/idea/.idea/libraries:truth",
    ],
)

stamped_plugin_xml(
    name = "java_plugin_xml",
    testonly = 1,
    plugin_id = "com.google.idea.blaze.java",
    plugin_name = "com.google.idea.blaze.java",
)

intellij_plugin(
    name = "java_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":java_plugin_xml",
    deps = [
        ":plugin_library",
        "//tools/adt/idea/aswb/base:plugin_library",
    ],
)

jvm_import(
    name = "java_plugin_import",
    testonly = 1,
    jars = [
        ":java_integration_test_plugin",
    ],
)

intellij_plugin_import(
    name = "com.google.idea.blaze.java",
    testonly = 1,
    overwrite_plugin_version = True,
    target_dir = "java",
    exports = [":java_plugin_import"],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(
        ["tests/integrationtests/**/*.java"],
    ),
    plugins = [":com.google.idea.blaze.java"],
    required_plugins = "com.google.idea.blaze.java",
    tags = [
        "noci:studio-win",
    ],
    test_package_root = "com.google.idea.blaze.java",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":java",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)
