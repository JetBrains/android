load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

alias(
    name = "project_alias",
    actual = "//simple_alias/deps:deps_alias",
)

java_library(
    name = "project_lib",
    srcs = glob(["java/com/example/project/*.java"]),
    deps = [":project_alias"],
)
