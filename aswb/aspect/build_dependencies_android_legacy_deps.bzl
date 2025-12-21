"""Loads and re-exports AndroidIdeInfo to support different versions of bazel. This is the legacy fallback implementation."""

def _get_legacy_android_info(target, rule):
    if hasattr(target, "android"):
        android = getattr(target, "android")

        generated_resource_files = []
        if hasattr(rule.attr, "resource_files"):
            for res_target in rule.attr.resource_files:
                generated_resource_files.extend([file for file in res_target.files.to_list() if not file.is_source])

        return struct(
            aar = android.aar,
            java_package = android.java_package,
            manifest = android.manifest,
            idl_class_jar = getattr(android.idl.output, "class_jar", None),
            idl_generated_java_files = getattr(android.idl, "generated_java_files", []),
            generated_resource_files = generated_resource_files,
        )
    return None

IDE_ANDROID = struct(
    get_android_info = _get_legacy_android_info,
)
