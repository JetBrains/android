load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

aswb_library(
    name = "QuerySummaryTestUtil",
    srcs = [
        "QuerySummaryTestBuilder.kt",
        "QuerySummaryTestUtil.java",
    ],
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:__subpackages__"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/adt/idea/aswb/shared",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "QuerySummaryTest",
    size = "small",
    srcs = ["QuerySummaryTest.java"],
    deps = [
        ":QuerySummaryTestUtil",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)

java_test(
    name = "QuerySpecTest",
    size = "small",
    srcs = ["QuerySpecTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)

java_test(
    name = "TruncatingFormattableTest",
    size = "small",
    srcs = ["TruncatingFormattableTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)
