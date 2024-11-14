package(
    default_visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
        "//tools/adt/idea/aswb/querysync:__subpackages__",
        "//tools/adt/idea/aswb/shared:__subpackages__",
    ],
)

java_test(
    name = "BuildArtifactCacheTest",
    size = "small",
    srcs = ["BuildArtifactCacheTest.java"],
    deps = [
        ":test_utils",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/artifact",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "test_utils",
    srcs = [
        "MockArtifact.java",
        "TestArtifactFetcher.java",
        "TestOutputArtifact.java",
    ],
    deps = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/artifact",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/exception",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "@maven//:com.google.guava.guava",
    ],
)
