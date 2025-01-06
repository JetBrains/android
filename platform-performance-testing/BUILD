load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "platform-performance-testing",
    iml_files = ["platform-performance-testing.iml"],
    test_srcs = ["src/main/java"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/perf-logger:studio.perf-logger[module]",
        "//tools/adt/idea/as-driver:as-driver.utils[module]",
    ],
)
