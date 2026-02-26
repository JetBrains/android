"""Common JVM flags for emulator tests."""

EMULATOR_PATH_JVM_FLAGS = select({
    "@platforms//os:linux": [
        "-Demulator.test.sdk.path=prebuilts/studio/sdk/linux",
        "-Demulator.test.emulator.path=prebuilts/studio/sdk/linux/emulator/emulator",
    ],
    "//tools/base/bazel/platforms:macos-arm64": [
        "-Demulator.test.sdk.path=prebuilts/studio/sdk/darwin",
        "-Demulator.test.emulator.path=prebuilts/studio/sdk/darwin/emulator-arm64/emulator",
    ],
    "@platforms//os:windows": [
        "-Demulator.test.sdk.path=prebuilts/studio/sdk/windows",
        "-Demulator.test.emulator.path=prebuilts/studio/sdk/windows/emulator/emulator",
    ],
    "//conditions:default": [
        "-Demulator.test.sdk.path=prebuilts/studio/sdk/darwin",
        "-Demulator.test.emulator.path=prebuilts/studio/sdk/darwin/emulator/emulator",
    ],
})

_SYSTEM_IMAGE_TO_SNAPSHOT = {
    "//prebuilts/studio/sdk:system_image_android-31_default": "//tools/adt/idea/emulator-snapshots:emulator_quickboot_api_31_snapshot",
    "//prebuilts/studio/sdk:system_image_android-33_default": "//tools/adt/idea/emulator-snapshots:emulator_quickboot_api_33_snapshot",
}

def emulator_test_jvm_flags(system_image):
    """Returns JVM flags for emulator tests without a snapshot."""
    return [
        "-Demulator.test.system.image.files=\"$(locations " + system_image + ")\"",
        "-Demulator.test.emulator.location=\"$(locations //prebuilts/studio/sdk:emulator)\"",
    ] + EMULATOR_PATH_JVM_FLAGS

def emulator_snapshot_jvm_flags(system_image):
    """Returns JVM flags for emulator tests with a snapshot."""

    flags = [
        "-Demulator.test.system.image.files=\"$(locations " + system_image + ")\"",
        "-Demulator.test.emulator.location=\"$(locations //prebuilts/studio/sdk:emulator)\"",
        "-Demulator.test.snapshot.path=\"$(location " + _SYSTEM_IMAGE_TO_SNAPSHOT.get(system_image) + ")\"",
    ]
    return flags + EMULATOR_PATH_JVM_FLAGS
