load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
])

java_proto_library(
    name = "querysummary_java_proto_internal",
    srcs = ["querysummary.proto"],
    visibility = ["//tools/adt/idea/aswb/querysync:__subpackages__"],
)

java_jarjar(
    name = "querysummary_java_proto",
    srcs = [
        ":querysummary_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
    visibility = ["//tools/adt/idea/aswb/querysync:__subpackages__"],
)

aswb_library(
    name = "query",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    deps = [
        ":querysummary_java_proto",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:proto",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
    ],
)
