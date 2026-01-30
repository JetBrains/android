load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/adt/idea/aswb/testing:test_defs.bzl", "aswb_test")

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
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)

aswb_test(
    name = "QuerySummaryTest",
    size = "small",
    srcs = ["QuerySummaryTest.java"],
    test_class = "com.google.idea.blaze.qsync.query.QuerySummaryTest",
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

aswb_test(
    name = "QuerySpecTest",
    size = "small",
    srcs = ["QuerySpecTest.java"],
    test_class = "com.google.idea.blaze.qsync.query.QuerySpecTest",
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)

aswb_test(
    name = "TruncatingFormattableTest",
    size = "small",
    srcs = ["TruncatingFormattableTest.java"],
    test_class = "com.google.idea.blaze.qsync.query.TruncatingFormattableTest",
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)
