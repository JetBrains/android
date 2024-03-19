load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib",
    srcs = ["src"],
    iml_files = ["intellij.android.layoutlib.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/adt/idea/.idea/libraries:asm-tools"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module]",
        "//tools/adt/idea/.idea/libraries:layoutlib",
        "//tools/base/environment-services:studio.intellij.android.environment-services[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib.tests",
    iml_files = ["intellij.android.layoutlib.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
    ],
)

# Build target to verify that layoutlib builds for tools/base
kotlin_library(
    name = "tools.layoutlib",
    srcs = glob(
        [
            "src/**/*.java",
            "src/**/*.kt",
        ],
    ),
    resource_strip_prefix = "tools/adt/idea/layoutlib/resources",
    resources = glob(["resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/layoutlib/data:layoutlib",
        "//tools/adt/idea/layoutlib-loader:tools.layoutlib-loader",
        "//tools/base/environment-services:tools.environment-services",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "@maven//:com.google.guava.guava",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
    ],
)
