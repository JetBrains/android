load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")

aswb_library(
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

java_proto_library(
    name = "artifact_tracker_state_java_proto",
    srcs = ["artifact_tracker_state.proto"],
    java_deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:snapshot_java_proto",
    ],
    proto_deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto_srcs",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:snapshot_java_proto_srcs",
    ],
    visibility = [
        "//tools/adt/idea/aswb/querysync:__subpackages__",
    ],
)
