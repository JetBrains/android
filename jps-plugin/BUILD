load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-jps-plugin",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/common:android-common[module]",
        "//tools/adt/idea/rt:android-rt[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.maven-jps-plugin[module, test]",
        "//tools/base/build-system:studio.manifest-merger[module]",
        "//tools:idea.platform-impl_and_others[module]",
    ],
)
