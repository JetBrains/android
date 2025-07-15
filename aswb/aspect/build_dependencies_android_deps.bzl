"""Loads and re-exports AndroidIdeInfo to support different versions of bazel. This is for bazel 7."""

# The native provider is available in Bazel 7 with --experimental_enable_android_migration_apis
ANDROID_IDE_INFO = native.AndroidIdeInfo if hasattr(native, "AndroidIdeInfo") else (
    android_common.AndroidIdeInfo if hasattr(android_common, "AndroidIdeInfo") else None
)

def _get_android_ide_info(target, rule):
    if ANDROID_IDE_INFO and ANDROID_IDE_INFO in target:
        android = target[ANDROID_IDE_INFO]
        return struct(
            aar = android.aar,
            java_package = android.java_package,
            manifest = android.manifest,
            idl_class_jar = android.idl_class_jar,
            idl_generated_java_files = android.idl_generated_java_files,
        )
    return None

IDE_ANDROID = struct(
    get_android_info = _get_android_ide_info,
)
