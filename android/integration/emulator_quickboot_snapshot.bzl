"""Bazel rule for generating an emulator quickboot snapshot."""

load("@bazel_skylib//rules:run_binary.bzl", "run_binary")
load("@rules_java//java:defs.bzl", "java_binary")

def emulator_quickboot_snapshot(name, system_image, jvm_flags = [], visibility = None):
    """Creates an emulator quickboot snapshot using the Emulator testlib.

    Args:
        name: The name of the resulting run_binary target.
        system_image: The system image to use for the snapshot.
        jvm_flags: Additional JVM flags to pass to the emulator tool.
        visibility: The visibility of the generated targets.
    """
    tool_name = name + "_tool"

    java_binary(
        name = tool_name,
        testonly = True,
        data = [
            "//prebuilts/studio/sdk:emulator",
            "//prebuilts/studio/sdk:platform-tools",
            system_image,
        ],
        jvm_flags = jvm_flags + [
            "-Demulator.test.system.image.files=\"$(locations " + system_image + ")\"",
        ] + select({
            "@platforms//os:linux": ["-Demulator.test.sdk.path=prebuilts/studio/sdk/linux"],
            "//conditions:default": ["-Demulator.test.sdk.path=prebuilts/studio/sdk/darwin"],
        }),
        main_class = "com.android.tools.idea.EmulatorQuickbootSnapshotGenerator",
        env = {
            "TEST_WORKSPACE": ".",
            "TEST_SRCDIR": ".",
        },
        runtime_deps = [
            "//tools/adt/idea/android/integration:emulator_quickboot_snapshot_generator_lib",
        ],
        target_compatible_with = select({
            "@platforms//os:windows": ["@platforms//:incompatible"],
            "//conditions:default": [],
        }),
        visibility = ["//visibility:private"],
    )

    run_binary(
        name = name,
        testonly = True,
        srcs = [
            "//prebuilts/studio/sdk:emulator",
            "//prebuilts/studio/sdk:platform-tools",
            "//tools/emulator/testlib/display:ffmpeg_files",
            "//tools/emulator/testlib/display:xvfb",
            system_image,
        ],
        outs = [name + ".zip"],
        args = ["$(location " + name + ".zip)"],
        env = {
            "TEST_WORKSPACE": ".",
            "TEST_SRCDIR": ".",
        },
        target_compatible_with = select({
            "@platforms//os:windows": ["@platforms//:incompatible"],
            "//conditions:default": [],
        }),
        tool = ":" + tool_name,
        visibility = visibility,
    )
