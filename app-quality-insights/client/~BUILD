load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.client",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.client.iml"],
    visibility = ["//visibility:public"],
    deps = ["@intellij//:intellij-sdk"],
)

# Build the client as a standalone library to ensure that unwanted dependencies from intellij-sdk
# are not used.
kotlin_library(
    name = "app-quality-insights-client-base",
    srcs = glob([
        "src/**/*.kt",
    ]),
)
