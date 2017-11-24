load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-gradle-jps",
    srcs = ["src"],
    iml_files = ["android-gradle-jps.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/adt/idea/jps-plugin:android-jps-plugin[module]",
        "//tools/adt/idea/build-common:build-common[module]",
        "//tools/idea/.idea/libraries:Gradle",
    ],
)
