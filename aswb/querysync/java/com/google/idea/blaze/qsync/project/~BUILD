load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
])

java_proto_library(
    name = "language_class_java_proto_internal",
    srcs = ["language_class.proto"],
    visibility = [
        "//tools/adt/idea/aswb/querysync:__subpackages__",
    ],
)

java_jarjar(
    name = "language_class_java_proto",
    srcs = [
        ":language_class_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
    visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
        "//tools/adt/idea/aswb/cpp:__subpackages__",
        "//tools/adt/idea/aswb/querysync:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    ],
)

java_proto_library(
    name = "snapshot_java_proto_internal",
    srcs = ["snapshot.proto"],
    java_deps = [
        ":language_class_java_proto_internal",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto_internal",
    ],
    proto_deps = [
        ":language_class_java_proto_internal_srcs",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto_internal_srcs",
    ],
    visibility = ["//tools/adt/idea/aswb/querysync:__subpackages__"],
)

java_jarjar(
    name = "snapshot_java_proto",
    srcs = [
        ":snapshot_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
    visibility = ["//tools/adt/idea/aswb/querysync:__subpackages__"],
)

aswb_library(
    name = "project",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    visibility = [
        "//tools/adt/idea/aswb/querysync:__subpackages__",
    ],
    deps = [
        ":language_class_java_proto",
        ":snapshot_java_proto",
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
        "//tools/vendor/google/aswb/plugin_api/maven:protobuf-java",
    ],
)
