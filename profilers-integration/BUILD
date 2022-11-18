load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.profilers.integration",
    iml_files = ["intellij.android.profilers.integration.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

maven_repository(
    name = "minapp_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.aapt2_7.1.0-7984345",
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.8.0-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.0-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.0-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.0-Beta",
    ],
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
)
