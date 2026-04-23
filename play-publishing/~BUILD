load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.play-publishing",
    srcs = ["src"],
    iml_files = ["intellij.android.play-publishing.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:intellij.libraries.grpc",
        "@intellij//:intellij.libraries.grpc.netty.shaded",
        "//tools/adt/idea/.idea/libraries:protobuf-kotlin",
        "//tools/adt/idea/play-publishing:play_publishing_kt_proto",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "play_publishing_kt_proto",
    jars = ["//tools/adt/idea/play-publishing/proto:play_publishing_kt_proto.jar"],
    visibility = ["//visibility:public"],
)
