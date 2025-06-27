load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "user",
    srcs = glob(["java/**/*.java"]),
    deps = [":generated"],
)

copy_file(
    name = "generated_src",
    src = "Generated.java_",
    out = "src/com/example/Generated.java",
)

java_library(
    name = "generated",
    srcs = [":generated_src"],
)
