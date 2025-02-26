load("//tools/base/bazel:proto.bzl", "java_proto_library")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib:__pkg__"],
)

java_proto_library(
    name = "build_event_stream_java_proto",
    srcs = ["build_event_stream.proto"],
    java_deps = [
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/packages/metrics:package_load_metrics_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:action_cache_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:command_line_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:failure_details_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:invocation_policy_java_proto",
    ],
    proto_deps = [
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/packages/metrics:package_load_metrics_java_proto_srcs",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:action_cache_java_proto_srcs",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:command_line_java_proto_srcs",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:failure_details_java_proto_srcs",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:invocation_policy_java_proto_srcs",
    ],
)
