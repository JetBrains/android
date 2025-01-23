package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
    "//tools/vendor/google3/aswb/java/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
])

proto_library(
    name = "language_class_proto",
    srcs = ["language_class.proto"],
)

java_proto_library(
    name = "language_class_java_proto",
    visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
        "//tools/adt/idea/aswb/cpp:__subpackages__",
        "//tools/adt/idea/aswb/querysync:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    ],
    deps = [":language_class_proto"],
)

proto_library(
    name = "project_proto",
    srcs = ["project.proto"],
    deps = [":language_class_proto"],
)

java_proto_library(
    name = "project_java_proto",
    visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
        "//tools/adt/idea/aswb/cpp:__subpackages__",
        "//tools/adt/idea/aswb/querysync:__subpackages__",
    ],
    deps = [":project_proto"],
)

proto_library(
    name = "snapshot_proto",
    srcs = ["snapshot.proto"],
    deps = [
        ":language_class_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary",
    ],
)

java_proto_library(
    name = "snapshot_java_proto",
    visibility = ["//tools/adt/idea/aswb/querysync:__subpackages__"],
    deps = [":snapshot_proto"],
)

java_library(
    name = "project",
    srcs = glob(["*.java"]),
    deps = [
        ":language_class_java_proto",
        ":project_java_proto",
        ":snapshot_java_proto",
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-protobuf-lite",
    ],
)
