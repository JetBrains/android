load("@rules_java//java:defs.bzl", "java_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

package(default_visibility = ["//visibility:public"])

FILES = [
    "action_cache",
    "android_deploy_info",
    "bazel_flags",
    "build",
    "builtin",
    "cache_salt",
    "crosstool_config",
    "deps",
    "desugar_deps",
    "execution_graph",
    "execution_statistics",
    "extra_actions_base",
    "failure_details",
    "invocation_policy",
    "java_compilation",
    "memory_pressure",
    "option_filters",
    "remote_scrubbing",
    "test_status",
    "worker_protocol",
]

[java_proto_library(
    name = s + "_java_proto_internal",
    srcs = [":" + s + ".proto"],
) for s in FILES]

[java_jarjar(
    name = s + "_java_proto",
    srcs = [":" + s + "_java_proto_internal"],
    rules = "//tools/base/bazel:jarjar_rules.txt",
) for s in FILES]

java_proto_library(
    name = "analysis_v2_java_proto_internal",
    srcs = ["analysis_v2.proto"],
    java_deps = [":build_java_proto_internal"],
    proto_deps = [":build_java_proto_internal_srcs"],
)

java_jarjar(
    name = "analysis_v2_java_proto",
    srcs = [
        ":analysis_v2_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)

java_proto_library(
    name = "command_line_java_proto_internal",
    srcs = ["command_line.proto"],
    java_deps = ["option_filters_java_proto_internal"],
    proto_deps = ["option_filters_java_proto_internal_srcs"],
)

java_jarjar(
    name = "command_line_java_proto",
    srcs = [
        ":command_line_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)

java_library(
    name = "all_protos",
    exports = [
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:%s_java_proto" % proto
        for proto in FILES
    ] + [
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:analysis_v2_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:command_line_java_proto",
    ],
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

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
)
