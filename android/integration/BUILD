load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.integration",
    iml_files = ["intellij.android.core.integration.iml"],
    test_srcs = ["testSrc"],
    test_tags = [
        "manual",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
    ],
)

java_test(
    name = "OpenProjectTest",
    timeout = "long",
    data = [
        ":minapp",
        ":test_deps",
        "//tools/adt/idea/studio:android-studio.linux.zip",
        "//tools/base/build-system:gradle-distrib-7.2",
    ],
    tags = [
        "block-network",
        "no_mac",
        "no_windows",
    ],
    test_class = "com.android.tools.idea.OpenProjectTest",
    runtime_deps = [":intellij.android.core.integration_testlib"],
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.5.31",
    ],
)
