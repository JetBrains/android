load("//tools/base/bazel:proto.bzl", "studio_java_proto_library")

studio_java_proto_library(
    name = "titan_java_proto",
    srcs = glob(["**/titan/*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    visibility = ["//visibility:public"],
)

studio_java_proto_library(
    name = "gemini_java_proto",
    srcs = glob(["**/gemini/*.proto"]),
    visibility = ["//visibility:public"],
)
