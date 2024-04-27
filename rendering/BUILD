load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.rendering",
    srcs = ["src"],
    iml_files = ["intellij.android.rendering.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/analytics-library/crash:analytics-crash[module]",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.rendering.tests",
    iml_files = ["intellij.android.rendering.tests.iml"],
    test_data = [
        "//tools/adt/idea/rendering/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:kxml2[test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/adt/idea/.idea/libraries:asm-tools[test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module, test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
    ],
)

# Build target to verify that rendering builds for tools/base
kotlin_library(
    name = "tools.rendering",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    lint_baseline = "lint_baseline_rendering.xml",
    resources = glob(["resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/adt/idea/layoutlib-loader:tools.layoutlib-loader",
        "//tools/adt/idea/render-resources:tools.render-resources",
        "//tools/analytics-library/crash:tools.analytics-crash",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.ow2.asm.asm",
    ],
)

# Build target to verify that rendering tests succeed for tools/base
kotlin_test(
    name = "tools.rendering.tests",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "testSrc/**/*.kt",
        "testSrc/**/*.java",
    ]),
    data = [
        "//tools/adt/idea/rendering/testData",
    ],
    jvm_flags = ["-Dtest.suite.jar=tools.rendering.tests.jar"],
    lint_baseline = "lint_baseline_tests.xml",
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.rendering",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/adt/idea/render-resources:tools.render-resources",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdk-common:tools.sdk-common.test.util",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:net.sf.kxml.kxml2",
    ],
)
