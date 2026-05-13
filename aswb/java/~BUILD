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
    name = "java_lib",
    srcs = glob(
        [
            "src/**/*.java",
            "src/**/*.kt",
        ],
    ),
    javacopts = ["-Xep:FutureReturnValueIgnored:OFF"],
    visibility = [
        "//tools/adt/idea/aswb:__subpackages__",
        "//tools/vendor/google3/aswb:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/actions",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/settings",
        "//tools/adt/idea/aswb/common/util:concurrency",
        "//tools/adt/idea/aswb/common/util:platform",
        "//tools/adt/idea/aswb/common/util:transactions",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",
        "//tools/adt/idea/aswb/plugin_api:junit",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
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
    name = "java",
    optional_plugin_xmls = [
        ":optional_java",
        ":optional_coverage",
    ],
    plugin_xmls = ["src/META-INF/blaze-java.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":java_lib"],
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
    test_package_root = "com.google.idea.bazel.java",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":java_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:coverage_for_tests",
        "//tools/adt/idea/aswb/plugin_api:jsr305",
        "//tools/adt/idea/aswb/plugin_api:test_libs",
        "//tools/adt/idea/aswb/plugin_api:truth",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
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

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(
        ["tests/integrationtests/**/*.java"],
    ),
    tags = [
        "noci:studio-win",
    ],
    test_package_root = "com.google.idea.bazel.java",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":java_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",
        "//tools/adt/idea/aswb/plugin_api:test_libs",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)
