# Description:
#
# Common test utilities for IntelliJ plugins.

load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "INTELLIJ_PLUGINS_VISIBILITY",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])

java_library(
    name = "lib",
    testonly = 1,
    srcs = glob([
        "src/**/*.java",
        "studio/**/*.java",
        "cidr/**/*.java",
    ]) + glob([
        "testcompat/v233/com/google/idea/cidr/**/*.java",
        "testcompat/v233/com/google/idea/sdkcompat/**/*.java",
        "testcompat/v233/com/google/idea/testing/**/*.java",
    ]),
    exports = ["//tools/adt/idea/aswb/testing/src/com/google/idea/testing/runfiles"],
    deps = [
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "runfiles",
    testonly = 1,
    exports = ["//tools/adt/idea/aswb/testing/src/com/google/idea/testing/runfiles"],
)
