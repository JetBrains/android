load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
)

package(default_visibility = COMMON_PLUGINS_VISIBILITY)

licenses(["notice"])

java_library(
    name = "concurrency",
    srcs = ["src/com/google/idea/common/util/ConcurrencyUtil.java"],
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep for @Nullable
    ],
)

java_library(
    name = "platform",
    srcs = [
        "src/com/google/idea/common/util/MorePlatformUtils.java",
    ],
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
    ],
)

java_library(
    name = "transactions",
    srcs = ["src/com/google/idea/common/util/Transactions.java"],
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
    ],
)

java_library(
    name = "process",
    srcs = glob(["src/com/google/idea/async/process/*.java"]),
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep for @Nullable
        "@maven//:com.google.errorprone.error_prone_annotations",
    ],
)

java_test(
    name = "CommandLineTaskTest",
    size = "medium",
    srcs = ["tests/unittests/com/google/idea/common/async/process/CommandLineTaskTest.java"],
    test_class = "com.google.idea.common.async.process.CommandLineTaskTest",
    deps = [
        ":process",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep for @Nullable
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "//tools/vendor/google/aswb/plugin_api:truth",
        "//tools/vendor/google/aswb/third_party/java/junit",
    ],
)
