package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
    "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
])

proto_library(
    name = "java_target_info",
    srcs = ["java_target_info.proto"],
    visibility = [
        "//tools/adt/idea/aswb/proto:__pkg__",
    ],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts:build_artifact_proto",
    ],
)

java_proto_library(
    name = "java_target_info_java_proto",
    deps = [":java_target_info"],
)

java_library(
    name = "java",
    srcs = glob(["*.java"]),
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/vendor/google/aswb/third_party/java/auto:auto_value",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared:artifact",
        "@maven//:com.google.guava.guava",
    ],
)
