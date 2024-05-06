load("//tools/base/bazel:utils.bzl", "java_jarjar")

# Expose "@android_system_logging_repo//:logcat_proto" as a target that `iml_to_build` can recognize
# TODO(b/304797004): Remove this target and .idea/libraries/logcat_proto.xml
java_jarjar(
    name = "logcat-proto",
    srcs = [
        "@android_system_logging_repo//:logcat-java_proto",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
