load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

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
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "QuerySyncTestUtilsTest",
    srcs = ["QuerySyncTestUtilsTest.java"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
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
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
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
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
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
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
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
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "AspectUnitTest",
    srcs = ["AspectUnitTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts:build_artifact_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:java_info",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
    ],
)
