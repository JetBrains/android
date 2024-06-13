load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

java_proto_library(
    name = "server-flag-test-proto",
    srcs = glob(["testSrc/com/android/tools/idea/serverflags/proto/*.proto"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.server-flags",
    srcs = ["src"],
    iml_files = ["intellij.android.server-flags.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/server-flags:libserver-flag-test-proto[test]",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "libserver-flag-test-proto",
    jars = ["//tools/adt/idea/server-flags:libserver-flag-test-proto.jar"],
    visibility = ["//visibility:public"],
)
