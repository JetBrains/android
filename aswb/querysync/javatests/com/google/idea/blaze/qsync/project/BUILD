load("//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "BuildGraphDataTest",
    srcs = ["BuildGraphDataTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:build_graphs",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "SnapshotSerializationTest",
    srcs = ["SnapshotSerializationTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared:vcs",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "ProjectDefinitionTest",
    size = "small",
    srcs = ["ProjectDefinitionTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "QuerySyncLanguageTest",
    size = "small",
    srcs = ["QuerySyncLanguageTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:io.grpc.grpc-protobuf",
    ],
)

java_test(
    name = "TestSourceGlobMatcherTest",
    size = "small",
    srcs = ["TestSourceGlobMatcherTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "DepsGraphTest",
    size = "small",
    srcs = ["DepsGraphTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "ExternalTransitiveClosureTest",
    size = "small",
    srcs = ["ExternalTransitiveClosureTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "PendingExternalDepsTest",
    size = "small",
    srcs = ["PendingExternalDepsTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)
