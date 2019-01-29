package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

java_proto_library(
    name = "nosyncbuilder_java_proto",
    srcs = glob(["*.proto"]),
)

java_jarjar(
    name = "nosyncbuilder-jarjar",
    srcs = [
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java-util/3.4.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
        "//tools/adt/idea/android/src/com/android/tools/idea/gradle/project/sync/ng/nosyncbuilder/proto:libnosyncbuilder_java_proto.jar",
    ],
    rules = "jarjar_rules.txt",
)

java_library(
    name = "nosyncbuilder-jarjar-lib",
    visibility = ["//visibility:public"],
    exports = [":nosyncbuilder-jarjar"],
    runtime_deps = [":nosyncbuilder-jarjar"],
)
