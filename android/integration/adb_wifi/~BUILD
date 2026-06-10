load("//tools/adt/idea/emulator-snapshots:emulator_test_env.bzl", "emulator_test_jvm_flags")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.integration.adb_wifi",
    enable_tests = False,
    iml_files = ["intellij.android.core.integration.adb_wifi.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/platform-performance-testing",
        "//tools/adt/idea/as-driver:as-driver.integration",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)

iml_test(
    name = "AdbWifiPairTest",
    timeout = "long",
    data = [
        "//prebuilts/studio/jdk:jdk21",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:system_image_android-CANARY_r11_default",
    ],
    exec_properties = {
        # needed to allow mDNS traffic.
        "dockerNetwork": "standard",
    },
    jvm_flags = emulator_test_jvm_flags("//prebuilts/studio/sdk:system_image_android-CANARY_r11_default"),
    module = ":intellij.android.core.integration.adb_wifi",
    tags = [
        "studio-e2e-test",
    ],
    tags_linux = [],
    tags_mac = [],
    tags_windows = [
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.adb_wifi.AdbWifiPairTest",
)

iml_test(
    name = "AdbWifiAutoEnableTest",
    timeout = "long",
    data = [
        "//prebuilts/studio/jdk:jdk21",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:system_image_android-CANARY_r11_default",
    ],
    exec_properties = {
        # needed to allow mDNS traffic.
        "dockerNetwork": "standard",
    },
    jvm_flags = emulator_test_jvm_flags("//prebuilts/studio/sdk:system_image_android-CANARY_r11_default"),
    module = ":intellij.android.core.integration.adb_wifi",
    tags = [
        "studio-e2e-test",
    ],
    tags_linux = [],
    tags_mac = [],
    tags_windows = [
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.adb_wifi.AdbWifiAutoEnableTest",
)
