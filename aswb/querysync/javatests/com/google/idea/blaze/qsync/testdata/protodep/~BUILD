load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

proto_library(
    name = "proto",
    srcs = ["testproto.proto"],
)

proto_library(
    name = "indirect_proto",
    deps = [":proto"],
)

java_proto_library(
    name = "proto_java_proto",
    deps = [":proto"],
)

java_proto_library(
    name = "indirect_proto_java_proto",
    deps = [":indirect_proto"],
)

java_library_for_test_data(
    name = "protodep",
    srcs = ["TestClassProtoDep.java"],
    deps = [":proto_java_proto"],
)

java_library_for_test_data(
    name = "indirect_protodep",
    srcs = ["TestClassProtoDep.java"],
    deps = [":indirect_proto_java_proto"],
)
