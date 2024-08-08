load("//tools/vendor/google/aswb:android.bzl", "android_library")

proto_library(
    name = "proto",
    srcs = ["testproto.proto"],
)

java_proto_library(
    name = "proto_java_proto",
    deps = [":proto"],
)

java_library(
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
