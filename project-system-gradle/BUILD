load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "project-system-gradle",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["project-system-gradle.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/adt/idea/project-system[module]",
        "//tools/adt/idea/android[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/base/apkparser/analyzer:studio.analyzer[module]",
        "//tools/idea/platform/lang-api[module]",
    ],
)
