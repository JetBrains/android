load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "ArtifactDirectoryUpdateTest",
    srcs = ["ArtifactDirectoryUpdateTest.java"],
    deps = [
        ":mock_artifact_cache",
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "mock_artifact_cache",
    srcs = ["MockArtifactCache.java"],
    deps = [
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "aspect_protos",
    srcs = ["AspectProtos.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts:build_artifact_java_proto",
        "@maven//:com.google.guava.guava",
    ],
)
