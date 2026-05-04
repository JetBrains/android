load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "cli_commands_proto",
    jars = ["//tools/adt/idea/cli-server/command-handler/proto:cli_commands_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "cli-server.command-handler",
    srcs = ["src"],
    iml_files = ["cli-server.command-handler.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/adt/idea/cli-server/command-handler:cli_commands_proto"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/cli-server/server:cli-server.server",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/cli-server/command-handler:cli_commands_proto",
    ],
)
