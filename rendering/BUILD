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
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/analytics-library/crash:analytics-crash",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
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
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk[test]",
        "//tools/base/common:studio.android.sdktools.common[test]",
        "//tools/adt/idea/rendering:intellij.android.rendering[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:kxml2[test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[test]",
        "//tools/adt/idea/.idea/libraries:asm-tools[test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
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
        "//tools/base/apkparser/analyzer:tools.analyzer",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
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
        "//prebuilts/studio/layoutlib/data:layoutlib",
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
        "@maven//:org.bouncycastle.bcpkix-jdk18on",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
        "@maven//:org.ow2.asm.asm-commons",
        "@maven//:org.ow2.asm.asm-util",
    ],
)
