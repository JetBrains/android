package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "java_proto_library")

FILES = [
    "action_cache",
    "android_deploy_info",
    "bazel_flags",
    "build",
    "builtin",
    "crosstool_config",
    "deps",
    "desugar_deps",
    "execution_statistics",
    "extra_actions_base",
    "invocation_policy",
    "java_compilation",
    "memory_pressure",
    "test_status",
    "worker_protocol",
    "execution_graph",
]

[java_proto_library(
    name = s + "_java_proto",
    srcs = [":" + s + ".proto"],
) for s in FILES]

java_proto_library(
    name = "analysis_v2_java_proto",
    srcs = ["analysis_v2.proto"],
    java_deps = [":build_java_proto"],
    proto_deps = [":build_java_proto_srcs"],
)

java_proto_library(
    name = "failure_details_java_proto",
    srcs = ["failure_details.proto"],
)

java_proto_library(
    name = "option_filters_java_proto",
    srcs = ["option_filters.proto"],
)

java_proto_library(
    name = "command_line_java_proto",
    srcs = ["command_line.proto"],
    java_deps = ["option_filters_java_proto"],
    proto_deps = ["option_filters_java_proto_srcs"],
)

# Proto needed by singlejar and embedded into the java tools archive.
genrule(
    name = "desugar_deps_zip",
    srcs = ["desugar_deps.proto"],
    outs = ["desugar_deps.zip"],
    cmd = "zip -q $@ $<",
    visibility = ["//src/tools/singlejar:__pkg__"],
)

filegroup(
    name = "desugar_deps_filegroup",
    srcs = ["desugar_deps.proto"],
    visibility = ["//src/tools/singlejar:__pkg__"],
)

java_proto_library(
    name = "cache_salt_java_proto",
    srcs = ["cache_salt.proto"],
)

java_proto_library(
    name = "remote_scrubbing_java_proto",
    srcs = ["remote_scrubbing.proto"],
)

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
)
