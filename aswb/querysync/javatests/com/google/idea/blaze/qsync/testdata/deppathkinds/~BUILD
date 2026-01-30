load("//tools/adt/idea/aswb:android.bzl", "android_library")
load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

proto_library(
    name = "proto",
    srcs = ["testproto.proto"],
)

java_proto_library(
    name = "proto_java_proto",
    deps = [":proto"],
)

java_library_for_test_data(
    name = "protodep",
    srcs = ["TestClassProtoDep.java"],
    deps = [":proto_java_proto"],
)

android_library(
    name = "android",
    srcs = [
        "AndroidOnlyFile.java",
        "TestClassProtoDep.java",
    ],
    manifest = "AndroidManifest.xml",
    deps = [":proto_java_proto"],
)

android_library(
    name = "android_using_java",
    srcs = [
        "AndroidUsingJava.java",
        "TestClassProtoDep.java",
    ],
    manifest = "AndroidManifest.xml",
    deps = [
        ":proto_java_proto",
        ":protodep",
    ],
)
