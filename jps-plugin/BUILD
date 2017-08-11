load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-jps-plugin",
    srcs = ["src"],
    iml_files = ["android-jps-plugin.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/build-common:build-common[module]",
        "//tools/adt/idea/rt:android-rt[module]",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/plugins/maven/jps-plugin:maven-jps-plugin[module, test]",
        "//tools/base/build-system:studio.manifest-merger[module]",
        "//tools/idea:platform-impl_and_others[module]",
    ],
)
