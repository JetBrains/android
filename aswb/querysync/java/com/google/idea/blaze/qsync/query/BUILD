load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")

package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
    "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
])

java_proto_library(
    name = "querysummary_java_proto",
    srcs = ["querysummary.proto"],
    visibility = ["//tools/adt/idea/aswb/querysync:__subpackages__"],
)

aswb_library(
    name = "query",
    srcs = glob(["*.java"]),
    deps = [
        ":querysummary_java_proto",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:proto",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "@maven//:com.google.guava.guava",
    ],
)
