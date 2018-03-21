load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "profilers-atrace",
    # do not sort: must match IML order
    srcs = [
        "src/core/common/src/main/kotlin",
        "src/core/model/src/main/kotlin",
    ],
    iml_files = ["profilers-atrace.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//tools/idea/.idea/libraries:KotlinJavaRuntime"],
)
