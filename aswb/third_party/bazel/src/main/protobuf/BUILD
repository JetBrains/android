package(default_visibility = ["//visibility:public"])

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

[proto_library(
    name = s + "_proto",
    srcs = [s + ".proto"],
) for s in FILES]

[java_proto_library(
    name = s + "_java_proto",
    deps = [":" + s + "_proto"],
) for s in FILES]

proto_library(
    name = "analysis_v2_proto",
    srcs = ["analysis_v2.proto"],
    deps = [":build_proto"],
)

java_proto_library(
    name = "analysis_v2_java_proto",
    deps = [":analysis_v2_proto"],
)

java_proto_library(
    name = "any_java_proto",
    deps = ["@com_google_protobuf//:any_proto"],
)

java_proto_library(
    name = "wrappers_java_proto",
    deps = ["@com_google_protobuf//:wrappers_proto"],
)

proto_library(
    name = "failure_details_proto",
    srcs = ["failure_details.proto"],
    deps = ["@com_google_protobuf//:descriptor_proto"],
)

java_proto_library(
    name = "failure_details_java_proto",
    deps = [":failure_details_proto"],
)

proto_library(
    name = "option_filters_proto",
    srcs = ["option_filters.proto"],
)

java_proto_library(
    name = "option_filters_java_proto",
    deps = [":option_filters_proto"],
)

proto_library(
    name = "command_line_proto",
    srcs = ["command_line.proto"],
    deps = [":option_filters_proto"],
)

java_proto_library(
    name = "command_line_java_proto",
    deps = [":command_line_proto"],
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

proto_library(
    name = "cache_salt_proto",
    srcs = ["cache_salt.proto"],
)

java_proto_library(
    name = "cache_salt_java_proto",
    deps = [":cache_salt_proto"],
)

proto_library(
    name = "remote_scrubbing_proto",
    srcs = ["remote_scrubbing.proto"],
)

java_proto_library(
    name = "remote_scrubbing_java_proto",
    deps = [":remote_scrubbing_proto"],
)

proto_library(
    name = "spawn_proto",
    srcs = ["spawn.proto"],
    deps = [
        "@com_google_protobuf//:duration_proto",
    ],
)

java_proto_library(
    name = "spawn_java_proto",
    deps = [":spawn_proto"],
)

proto_library(
    name = "xcode_proto",
    srcs = ["xcode_config.proto"],
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "xcode_java_proto",
    deps = [":xcode_proto"],
)

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
)
