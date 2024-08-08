load("//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "NewArtifactTrackerTest",
    size = "small",
    srcs = ["NewArtifactTrackerTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/artifacts:aspect_protos",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared:artifact",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "@maven//:com.google.guava.guava",
        "@maven//:org.mockito.mockito-core",
    ],
)

java_test(
    name = "ArtifactTrackerStateSerializationTest",
    size = "small",
    srcs = ["ArtifactTrackerStateSerializationTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps:artifact_tracker_state_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared:vcs",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "ArtifactDirectoryBuilderTest",
    size = "small",
    srcs = ["ArtifactDirectoryBuilderTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com.google.guava.guava",
    ],
)
