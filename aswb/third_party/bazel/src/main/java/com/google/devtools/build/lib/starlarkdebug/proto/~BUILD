load("@rules_java//java:defs.bzl", "java_proto_library")

package(default_visibility = ["//src:__subpackages__"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib:__pkg__"],
)

java_proto_library(
    name = "starlark_debugging_java_proto",
    # The Bazel IntelliJ plugin uses this.
    # Other clients must not assume protocol stability.
    visibility = ["//visibility:public"],
    deps = ["starlark_debugging_proto"],
)

proto_library(
    name = "starlark_debugging_proto",
    srcs = ["starlark_debugging.proto"],
)
