"""Loads and re-exports AndroidIdeInfo to support different versions of bazel. This is for bazel 8 or later"""

load("@rules_android//providers:providers.bzl", "AndroidIdeInfo")

def _get_android_ide_info(target, rule):
    if AndroidIdeInfo and AndroidIdeInfo in target:
        android = target[AndroidIdeInfo]

        generated_resource_files = []
        if hasattr(rule.attr, "resource_files"):
            for res_target in rule.attr.resource_files:
                generated_resource_files.extend([file for file in res_target.files.to_list() if not file.is_source])

        return struct(
            aar = android.aar,
            java_package = android.java_package,
            manifest = android.manifest,
            idl_class_jar = android.idl_class_jar,
            idl_generated_java_files = android.idl_generated_java_files,
            generated_resource_files = generated_resource_files,
        )
    return None

IDE_ANDROID = struct(
    get_android_info = _get_android_ide_info,
)
