load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib:__pkg__"],
)

java_proto_library(
    name = "build_event_stream_java_proto_internal",
    srcs = ["build_event_stream.proto"],
    java_deps = [
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/packages/metrics:package_load_metrics_java_proto_internal",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:action_cache_java_proto_internal",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:command_line_java_proto_internal",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:failure_details_java_proto_internal",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:invocation_policy_java_proto_internal",
    ],
    proto_deps = [
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/packages/metrics:package_load_metrics_java_proto_internal_srcs",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:action_cache_java_proto_internal_srcs",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:command_line_java_proto_internal_srcs",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:failure_details_java_proto_internal_srcs",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:invocation_policy_java_proto_internal_srcs",
    ],
)

java_jarjar(
    name = "build_event_stream_java_proto",
    srcs = [
        ":build_event_stream_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)
