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

java_library(
    name = "protodep",
    srcs = ["TestClassProtoDep.java"],
    deps = [":proto_java_proto"],
)

java_library(
    name = "indirect_protodep",
    srcs = ["TestClassProtoDep.java"],
    deps = [":indirect_proto_java_proto"],
)
