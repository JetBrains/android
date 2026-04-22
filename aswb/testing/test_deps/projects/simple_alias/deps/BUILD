package(default_visibility = ["//visibility:public"])

java_library(
    name = "true_lib",
    srcs = glob(["java/com/example/deps/*.java"]),
)

alias(
    name = "deps_alias",
    actual = ":true_lib",
)
