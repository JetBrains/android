load("//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_library(
    name = "QuerySummaryTestUtil",
    srcs = [
        "QuerySummaryTestBuilder.java",
        "QuerySummaryTestUtil.java",
    ],
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:__subpackages__"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
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
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
    ],
)

java_test(
    name = "QuerySpecTest",
    size = "small",
    srcs = ["QuerySpecTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
    ],
)
