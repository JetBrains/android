load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.integration",
    enable_tests = False,
    iml_files = ["intellij.android.core.integration.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/vendor/google/firebase[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//tools/adt/idea/.idea/libraries:firebase_java_proto[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
    ],
)

iml_test(
    name = "OpenProjectTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
    }) + [
        ":minapp",
        ":openproject_deps",
        "//tools/base/build-system:gradle-distrib-7.2",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "no_windows",  # Gradle-sync error on Windows. Probably fixable with a different project.
    ],
    test_class = "com.android.tools.idea.OpenProjectTest",
)

iml_test(
    name = "EmulatorTest",
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "no_mac",  # Path issues, probably easily fixable.
        "no_windows",  # Path issues, probably easily fixable.
    ],
    test_class = "com.android.tools.idea.EmulatorTest",
)

iml_test(
    name = "AppInsightsTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
    }) + [
        ":appinsights",
        ":openproject_deps",
        "//tools/base/build-system:gradle-distrib-7.2",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "no_mac",  # Untested.
        "no_windows",
    ],
    test_class = "com.google.services.firebase.insights.AppInsightsTest",
)

maven_repository(
    name = "openproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.5.31",
    ],
)

iml_test(
    name = "BuildProjectTest",
    timeout = "long",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
    }) + [
        ":buildproject_deps",
        ":minapp",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//tools/base/build-system:gradle-distrib-7.2",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "no_mac",
        "no_windows",
    ],
    test_class = "com.android.tools.idea.BuildProjectTest",
)

maven_repository(
    name = "buildproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.aapt2_7.1.0-7984345",
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.5.31",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.5.31",
    ],
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
)

filegroup(
    name = "appinsights",
    srcs = glob(["testData/appinsights/**"]),
)
