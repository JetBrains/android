"""Loads and re-exports AndroidIdeInfo to support different versions of bazel. This is the legacy fallback implementation."""

def _get_legacy_android_info(target, rule):
    if hasattr(target, "android"):
        android = getattr(target, "android")
        return struct(
            aar = android.aar,
            java_package = android.java_package,
            manifest = android.manifest,
            idl_class_jar = getattr(android.idl.output, "class_jar", None),
            idl_generated_java_files = getattr(android.idl, "generated_java_files", []),
        )
    return None

IDE_ANDROID = struct(
    get_android_info = _get_legacy_android_info,
)
