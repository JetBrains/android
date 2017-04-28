load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "sherpa-ui",
    srcs = ["src"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/sherpa:sherpa-solver[module]"],
)
