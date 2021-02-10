load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

java_proto_library(
    name = "server-flag-test-proto",
    srcs = glob(["testSrc/com/android/tools/idea/serverflags/proto/*.proto"]),
    protoc_version = "3.0.0",
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
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:protobuf",
        "//tools/adt/idea/server-flags:libserver-flag-test-proto[test]",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "libserver-flag-test-proto",
    jars = ["//tools/adt/idea/server-flags:libserver-flag-test-proto.jar"],
    visibility = ["//visibility:public"],
)
