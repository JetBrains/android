load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(
    default_visibility = DEFAULT_TEST_VISIBILITY,
)

proto_library(
    name = "test_proto",
    srcs = ["test_message.proto"],
)

java_proto_library(
    name = "test_java_proto",
    deps = [":test_proto"],
)

java_test(
    name = "ProtoStringInternerTest",
    size = "small",
    srcs = ["ProtoStringInternerTest.java"],
    deps = [
        ":test_java_proto",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/proto",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-protobuf",
    ],
)
