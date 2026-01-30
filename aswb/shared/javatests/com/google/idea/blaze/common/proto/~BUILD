load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")
load("//tools/adt/idea/aswb/testing:test_defs.bzl", "aswb_test")

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

aswb_test(
    name = "ProtoStringInternerTest",
    size = "small",
    srcs = ["ProtoStringInternerTest.java"],
    test_class = "com.google.idea.blaze.common.proto.ProtoStringInternerTest",
    deps = [
        ":test_java_proto",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/proto",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api/maven:grpc-protobuf",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)
