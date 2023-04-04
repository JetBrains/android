load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.rendering",
    srcs = ["src"],
    iml_files = ["intellij.android.rendering.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.rendering.tests",
    iml_files = ["intellij.android.rendering.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module, test]",
    ],
)

# Build target to verify that rendering builds for tools/base
kotlin_library(
    name = "tools.rendering",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdklib:tools.sdklib",
    ],
)

# Build target to verify that rendering tests succeed for tools/base
kotlin_test(
    name = "tools.rendering.tests",
    srcs = glob([
        "testSrc/**/*.kt",
        "testSrc/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tools.rendering.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.rendering",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
