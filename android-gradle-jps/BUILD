load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-gradle-jps",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/adt/idea/jps-plugin:android-jps-plugin[module]",
        "//tools/adt/idea/common:android-common[module]",
        "//tools/adt/idea/android[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/plugins/gradle[module]",
    ],
)
