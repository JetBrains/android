load("@rules_android//android:rules.bzl", "android_binary")
load("@rules_cc//cc:cc_library.bzl", "cc_library")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "cc1",
    srcs = ["TestClass.cc"],
    hdrs = [
        "TestClass.h",
        "//cc1/project/gen:generated_header",
    ],
    linkopts = ["-llog"],
)

android_binary(
    name = "project",
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        ":lib",
    ],
)

android_library(
    name = "lib",
    srcs = glob(["com/jniexample/*.java"]),
    deps = [
        ":hellojni",
    ],
)

cc_library(
    name = "hellojni",
    srcs = ["hello-jni.cc"],
    deps = [":hello-api"],
)

cc_library(
    name = "hello-api",
    srcs = ["hello-api.cc"],
    hdrs = ["hello-api.h"],
    deps = [":cc1"],
)
