"""Loads and re-exports AndroidIdeInfo to support different versions of bazel. This is for bazel 8 or later"""

load("@rules_android//providers:providers.bzl", "AndroidIdeInfo")

def _get_android_ide_info(target, rule):
    if AndroidIdeInfo and AndroidIdeInfo in target:
        android = target[AndroidIdeInfo]
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
