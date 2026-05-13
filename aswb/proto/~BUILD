#
# Description:
# Proto dependencies from bazel.
#
load("@rules_java//java:defs.bzl", "java_library")
load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
    "create_proto_visibility_group",
)

licenses(["notice"])

create_proto_visibility_group()

# TODO delete this build rule. Dependants should use the proto targets they use
# directly instead.
java_library(
    name = "proto_deps",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/buildeventstream/proto:build_event_stream_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/packages/metrics:package_load_metrics_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/packages/metrics:package_metrics_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib/starlarkdebug/proto:starlark_debugging_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:all_protos",
    ],
)
