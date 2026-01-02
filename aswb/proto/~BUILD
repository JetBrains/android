#
# Description:
# Proto dependencies from bazel.
#
load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "ASPECT_PROTO_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
    "create_proto_visibility_group",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "combine_visibilities",
)
load("//tools/base/bazel:proto.bzl", "java_proto_library")

licenses(["notice"])

create_proto_visibility_group()

# TODO delete this build rule. Dependants should use the proto targets they use
# directly instead.
java_library(
    name = "proto_deps",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        # do not sort: to get a consistent version of the protobuf-java library across all proto users
        "@maven//:io.grpc.grpc-protobuf",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/buildeventstream/proto:build_event_stream_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/starlarkdebug/proto:starlark_debugging_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:android_deploy_info_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:build_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:command_line_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:deps_java_proto",
    ],
)
