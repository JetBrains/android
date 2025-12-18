load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.client",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.client.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:gradle-shared-proto",
        "//tools/adt/idea/.idea/libraries:google-api-client",
        "@intellij//:intellij.libraries.grpc",
    ],
)

# Build the client as a standalone library to ensure that unwanted dependencies from intellij-sdk
# are not used.
kotlin_library(
    name = "app-quality-insights-client-base",
    srcs = glob([
        "src/**/*.kt",
    ]),
    deps = [
        "//tools/adt/idea/.idea/libraries:gradle-shared-proto",
        "@maven//:com.google.api-client.google-api-client",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)
