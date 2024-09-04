package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
    "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
])

proto_library(
    name = "querysummary",
    srcs = ["querysummary.proto"],
)

java_proto_library(
    name = "querysummary_java_proto",
    visibility = ["//tools/adt/idea/aswb/querysync:__subpackages__"],
    deps = [":querysummary"],
)

java_library(
    name = "query",
    srcs = glob(["*.java"]),
    deps = [
        ":querysummary_java_proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:build_java_proto",
        "//tools/vendor/google/aswb/third_party/java/auto:auto_value",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared",
        "//tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/shared:proto",
        "@maven//:com.google.guava.guava",
    ],
)
