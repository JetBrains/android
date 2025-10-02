load("@rules_android//android:rules.bzl", "android_library")

android_library(
    name = "basic_lib",
    srcs = ["BasicActivity.java"],
    custom_package = "com.basicapp",
    javacopts = ["-source 11 -target 11"],
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    visibility = ["//visibility:public"],
)
