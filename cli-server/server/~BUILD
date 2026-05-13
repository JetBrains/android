load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "cli-server.server",
    srcs = ["src"],
    iml_files = ["cli-server.server.iml"],
    resources = ["resources"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    exports = ["//tools/adt/idea/cli-server/server:studio_cli_service_kt_proto"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:intellij-sdk",
        "@intellij//:intellij.libraries.grpc",
        "//tools/adt/idea/.idea/libraries:protobuf-kotlin",
        "//tools/adt/idea/cli-server/server:studio_cli_service_kt_proto",
        "//tools/base/common:studio.android.sdktools.common",
        "@intellij//:intellij.libraries.grpc.netty.shaded",
        "//tools/adt/idea/android-common:intellij.android.common",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "studio_cli_service_kt_proto",
    jars = ["//tools/adt/idea/cli-server/server/proto:studio_cli_service_kt_proto.jar"],
    visibility = ["//visibility:public"],
)
