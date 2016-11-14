load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-ui-model",
    srcs = ["src/main/java"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.platform-api[module]",
    ],
)
