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
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
    }) + [
        ":minapp",
        ":openproject_deps",
        "//tools/base/build-system:gradle-distrib-7.2",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
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
        "no_windows",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.EmulatorTest",
)

iml_test(
    name = "AdbServerConnectionTest",
    data = [
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "manual",  # Adb startup error: "could not install *smartsocket* listener: Address already in use"
        "no_mac",  # Same issue as Emulator test (path issues)
        "no_windows",  # Same issue as Emulator test (path issues)
    ],
    test_class = "com.android.tools.idea.AdbServerConnectionTest",
)

iml_test(
    name = "EmulatorBeforeStudioTest",
    data = [
        ":minapp",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/adt/idea/studio:android-studio.linux.zip",
        "//tools/base/build-system:gradle-distrib-7.2",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "no_mac",  # Untested
        "no_windows",  # Untested
    ],
    test_class = "com.android.tools.idea.EmulatorBeforeStudioTest",
)

iml_test(
    name = "EmulatorAfterStudioTest",
    data = [
        ":minapp",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/adt/idea/studio:android-studio.linux.zip",
        "//tools/base/build-system:gradle-distrib-7.2",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "manual",  # Test occasionally fails because emulator can't fully boot
        "no_mac",  # Untested
        "no_windows",  # Untested
    ],
    test_class = "com.android.tools.idea.EmulatorAfterStudioTest",
)

iml_test(
    name = "BuildAndRunTest",
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
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/base/build-system:gradle-distrib-7.2",
        "@system_image_android-29_default_x86_64//:x86_64-android-29-images",
    ],
    module = ":intellij.android.core.integration",
    tags = [
        "block-network",
        "no_mac",  # Untested
        "no_windows",  # Untested
    ],
    test_class = "com.android.tools.idea.BuildAndRunTest",
)

maven_repository(
    name = "openproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.0",
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
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
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
    ],
    test_class = "com.android.tools.idea.BuildProjectTest",
)

maven_repository(
    name = "buildproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.aapt2_7.1.0-7984345",
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.0",
    ],
)

filegroup(
    name = "minapp",
    srcs = glob(["testData/minapp/**"]),
)
