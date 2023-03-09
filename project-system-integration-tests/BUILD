load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

common_data = select({
    "@platforms//os:linux": [
        "//tools/adt/idea/studio:android-studio.linux.zip",
    ],
    "@platforms//os:macos": [
        "//tools/adt/idea/studio:android-studio.mac.zip",
    ],
    "@platforms//os:windows": [
        "//tools/adt/idea/studio:android-studio.win.zip",
    ],
}) + [
    ":sdkindexapp",
    ":sdkindexproject_deps",
    ":sdkindexsnapshot",
    "//prebuilts/studio/sdk:build-tools/latest",
    "//tools/base/build-system:gradle-distrib-7.3.3",
]

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.integration.tests",
    enable_tests = False,
    iml_files = ["intellij.android.projectSystem.integration.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
    ],
)

iml_test(
    name = "PolicyIssuesPsdTest",
    timeout = "long",
    data = common_data,
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.PolicyIssuesPsdTest",
)

iml_test(
    name = "SdkIndexLintTest",
    timeout = "long",
    data = common_data,
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.SdkIndexLintTest",
)

iml_test(
    name = "SdkIndexPsdTest",
    timeout = "long",
    data = common_data,
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.SdkIndexPsdTest",
)

maven_repository(
    name = "sdkindexproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.20-Beta",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

filegroup(
    name = "sdkindexapp",
    srcs = glob(["testData/sdkindexapp/**"]),
)

filegroup(
    name = "sdkindexsnapshot",
    srcs = glob(["testData/snapshot/**"]),
)
