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
    "//prebuilts/studio/sdk:system_image_android-30_default": "//tools/adt/idea/emulator-snapshots:emulator_quickboot_api_30_snapshot",
    "//prebuilts/studio/sdk:system_image_android-31_default": "//tools/adt/idea/emulator-snapshots:emulator_quickboot_api_31_snapshot",
    "//prebuilts/studio/sdk:system_image_android-33_default": "//tools/adt/idea/emulator-snapshots:emulator_quickboot_api_33_snapshot",
    "//prebuilts/studio/sdk:system_image_android-33_aosp_atd": "//tools/adt/idea/emulator-snapshots:emulator_quickboot_api_33_aosp_atd_snapshot",
    "//prebuilts/studio/sdk:system_image_android-35_default": "//tools/adt/idea/emulator-snapshots:emulator_quickboot_api_35_snapshot",
}

def emulator_test_jvm_flags(system_image):
    """Returns JVM flags for emulator tests without a snapshot.

    Args:
        system_image: The label of the system image to use.

    Returns:
        A list of JVM flags strings.
    """
    return [
        "-Demulator.test.system.image.files=\"$(locations " + system_image + ")\"",
        "-Demulator.test.emulator.location=\"$(locations //prebuilts/studio/sdk:emulator)\"",
    ] + EMULATOR_PATH_JVM_FLAGS

def _if_snapshots_enabled(value):
    return select({
        "//tools/adt/idea/emulator-snapshots:snapshots_enabled_linux": value,
        "//tools/adt/idea/emulator-snapshots:snapshots_enabled_macos": value,
        "//conditions:default": [],
    })

def emulator_snapshot_jvm_flags(system_image):
    """Returns JVM flags for emulator tests, using a snapshot if enabled.

    Args:
        system_image: The label of the system image to use.

    Returns:
        A select() or list of JVM flags strings.
    """
    snapshot = _SYSTEM_IMAGE_TO_SNAPSHOT.get(system_image)
    if not snapshot:
        return emulator_test_jvm_flags(system_image)

    return emulator_test_jvm_flags(system_image) + _if_snapshots_enabled(["-Demulator.test.snapshot.path=\"$(location " + snapshot + ")\""])

def emulator_snapshot_data(system_image):
    """Returns the snapshot ZIP dependency for emulator tests if snapshots are enabled.

    Args:
        system_image: The label of the system image to use.

    Returns:
        A select() or list of labels representing the snapshot dependency.
    """
    snapshot = _SYSTEM_IMAGE_TO_SNAPSHOT.get(system_image)
    if not snapshot:
        return []

    return _if_snapshots_enabled([snapshot])
