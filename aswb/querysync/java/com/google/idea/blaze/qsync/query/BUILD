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
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:proto",
        "//tools/adt/idea/aswb/third_party/bazel/src/main/protobuf:build_java_proto",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "@maven//:com.google.guava.guava",
    ],
)
