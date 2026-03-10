load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

package(default_visibility = ["//src:__subpackages__"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//tools/adt/idea/aswb/third_party/bazel/src/main/java/com/google/devtools/build/lib:__pkg__"],
)

# The Bazel IntelliJ plugin uses this.
# Other clients must not assume protocol stability.
java_proto_library(
    name = "starlark_debugging_java_proto_internal",
    srcs = ["starlark_debugging.proto"],
)

java_jarjar(
    name = "starlark_debugging_java_proto",
    srcs = [
        ":starlark_debugging_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
