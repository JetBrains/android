package(
    default_visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects/proto1:__subpackages__",
    ],
)

proto_library(
    name = "external_proto",
    srcs = ["external.proto"],
)

java_proto_library(
    name = "external_java_proto",
    deps = [
        ":external_proto",
    ],
)

# TODO: b/337007753 - Fix protobuf tests.
#java_lite_proto_library(
java_proto_library(
    name = "external_java_proto_lite",
    deps = [
        ":external_proto",
    ],
)
