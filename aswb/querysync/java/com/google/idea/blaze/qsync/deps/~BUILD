java_library(
    name = "deps",
    srcs = glob(["*.java"]),
    visibility = [
        "//tools/adt/idea/aswb/querysync:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
    deps = [
        ":artifact_tracker_state_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc:cc_compilation_info_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared:proto",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-protobuf",
    ],
)

proto_library(
    name = "artifact_tracker_state_proto",
    srcs = ["artifact_tracker_state.proto"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:snapshot_proto",
    ],
)

java_proto_library(
    name = "artifact_tracker_state_java_proto",
    visibility = [
        "//tools/adt/idea/aswb/querysync:__subpackages__",
    ],
    deps = [":artifact_tracker_state_proto"],
)
