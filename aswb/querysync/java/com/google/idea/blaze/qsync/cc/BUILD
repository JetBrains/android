load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")

package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
    "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
])

aswb_library(
    name = "cc",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    deps = [
        ":cc_compilation_info_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "@maven//:com.google.guava.guava",
    ],
)

java_proto_library(
    name = "cc_compilation_info_java_proto",
    srcs = ["cc_compilation_info.proto"],
    java_deps = ["//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts:build_artifact_java_proto"],
    proto_deps = ["//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts:build_artifact_java_proto_srcs"],
)
