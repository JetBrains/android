load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-jps-plugin",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea:android-common[module]",
        "//tools/adt/idea:android-rt[module]",
        "//tools:idea.jps-builders[module]",
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools:idea.jps-model-api[module]",
        "//tools:idea.jps-model-serialization[module]",
        "//tools:idea.jps-model-impl[module]",
        "//tools:idea.maven-jps-plugin[module, test]",
        "//tools/base/build-system:studio.manifest-merger[module]",
        "//tools:idea.MM_RegExpSupport[module]",
    ],
)
