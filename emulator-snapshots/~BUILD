load("@bazel_skylib//rules:common_settings.bzl", "bool_flag")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_SYSTEM_IMAGE")
load("//tools/adt/idea/emulator-snapshots:emulator_quickboot_snapshot.bzl", "emulator_quickboot_snapshot")
load("//tools/adt/idea/emulator-snapshots:emulator_test_env.bzl", "emulator_test_jvm_flags")
load("//tools/base/bazel:bazel.bzl", "iml_module")

bool_flag(
    name = "enable_snapshots",
    build_setting_default = False,
    visibility = ["//visibility:public"],
)

config_setting(
    name = "use_snapshots",
    flag_values = {
        ":enable_snapshots": "True",
    },
    visibility = ["//visibility:public"],
)

java_library(
    name = "emulator_quickboot_snapshot_generator_lib",
    testonly = True,
    srcs = ["testSrc/com/android/tools/idea/EmulatorQuickbootSnapshotGenerator.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/emulator/testlib",
    ],
)

emulator_quickboot_snapshot(
    name = "emulator_quickboot_api_30_snapshot",
    jvm_flags = emulator_test_jvm_flags("//prebuilts/studio/sdk:system_image_android-30_default"),
    system_image = "//prebuilts/studio/sdk:system_image_android-30_default",
    visibility = ["//visibility:public"],
)

emulator_quickboot_snapshot(
    name = "emulator_quickboot_api_31_snapshot",
    jvm_flags = emulator_test_jvm_flags(INTEGRATION_TEST_SYSTEM_IMAGE),
    system_image = INTEGRATION_TEST_SYSTEM_IMAGE,
    visibility = ["//visibility:public"],
)

emulator_quickboot_snapshot(
    name = "emulator_quickboot_api_33_snapshot",
    jvm_flags = emulator_test_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_default"),
    system_image = "//prebuilts/studio/sdk:system_image_android-33_default",
    visibility = ["//visibility:public"],
)

emulator_quickboot_snapshot(
    name = "emulator_quickboot_api_33_aosp_atd_snapshot",
    jvm_flags = emulator_test_jvm_flags("//prebuilts/studio/sdk:system_image_android-33_aosp_atd"),
    system_image = "//prebuilts/studio/sdk:system_image_android-33_aosp_atd",
    visibility = ["//visibility:public"],
)

emulator_quickboot_snapshot(
    name = "emulator_quickboot_api_35_snapshot",
    jvm_flags = emulator_test_jvm_flags("//prebuilts/studio/sdk:system_image_android-35_default"),
    system_image = "//prebuilts/studio/sdk:system_image_android-35_default",
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "emulator-snapshots",
    enable_tests = False,
    iml_files = ["emulator-snapshots.iml"],
    test_deps = ["//tools/emulator/testlib:android.sdktools.testlib"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    deps = ["@intellij//:intellij-sdk"],
)
