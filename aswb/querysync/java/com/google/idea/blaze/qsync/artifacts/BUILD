java_library(
    name = "artifacts",
    srcs = glob(["*.java"]),
    visibility = [
        "//tools/adt/idea/aswb/querysync:__subpackages__",
        "//tools/vendor/google3/aswb/java/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
    deps = [
        ":build_artifact_java_proto",
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps:artifact_tracker_state_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared:exception",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:io.grpc.grpc-protobuf-lite",
    ],
)

proto_library(
    name = "build_artifact_proto",
    srcs = ["build_artifact.proto"],
    visibility = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc:__pkg__",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    ],
)

java_proto_library(
    name = "build_artifact_java_proto",
    visibility = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc:__pkg__",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java:__pkg__",
        "//tools/adt/idea/aswb/querysync/javatests:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
    deps = [":build_artifact_proto"],
)
