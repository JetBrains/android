load("//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_library(
    name = "TestUtils",
    testonly = 1,
    srcs = [
        "GraphToProjectConverters.java",
        "QuerySyncTestUtils.java",
        "TestDataSyncRunner.java",
    ],
    visibility = [
        ":__subpackages__",
        "//tools/adt/idea/aswb/base:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/vendor/google/aswb/third_party/java/auto:auto_value",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared:vcs",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared/javatests/com/google/idea/blaze/common:test_utils",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "QuerySyncTestUtilsTest",
    srcs = ["QuerySyncTestUtilsTest.java"],
    deps = [
        ":TestUtils",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
    ],
)

java_test(
    name = "GraphToProjectConverterTest",
    srcs = ["GraphToProjectConverterTest.java"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:build_graphs",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "AffectedPackagesTest",
    size = "small",
    srcs = ["AffectedPackagesTest.java"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared:vcs",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "ProjectRefresherTest",
    size = "small",
    srcs = ["ProjectRefresherTest.java"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared:vcs",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "PartialProjectRefreshTest",
    size = "small",
    srcs = ["PartialProjectRefreshTest.java"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
        "@maven//:com.google.guava.guava",
    ],
)
