"""Loads and re-exports dependencies of build_dependencies.bzl to support different versions of bazel"""

load(
    "@bazel_tools//tools/build_defs/cc:action_names.bzl",
    _CPP_COMPILE_ACTION_NAME = "CPP_COMPILE_ACTION_NAME",
    _C_COMPILE_ACTION_NAME = "C_COMPILE_ACTION_NAME",
)

ZIP_TOOL_LABEL = "@bazel_tools//tools/zip:zipper"

ANDROID_IDE_INFO = None

# KOTLIN

def _get_dependency_attribute(rule, attr):
    if hasattr(rule.attr, attr):
        to_add = getattr(rule.attr, attr)
        if type(to_add) == "list":
            return [t for t in to_add if type(t) == "Target"]
        elif type(to_add) == "Target":
            return [to_add]
    return []

def _get_followed_kotlin_dependencies(rule):
    deps = []
    if rule.kind in ["kt_jvm_library_helper", "kt_android_library", "android_library"]:
        deps.extend(_get_dependency_attribute(rule, "_toolchain"))
    if rule.kind in ["kt_jvm_toolchain"]:
        deps.extend(_get_dependency_attribute(rule, "kotlin_libs"))
    return deps

IDE_KOTLIN = struct(
    srcs_attributes = [
        "kotlin_srcs",
        "kotlin_test_srcs",
        "common_srcs",
    ],
    follow_attributes = [],
    follow_additional_attributes = [
        "_toolchain",
        "kotlin_libs",
    ],
    followed_dependencies = _get_followed_kotlin_dependencies,
    toolchains_aspects = [],
)

# CC

def _get_cc_toolchain_target(rule):
    if hasattr(rule.attr, "_cc_toolchain"):
        return getattr(rule.attr, "_cc_toolchain")
    return None

IDE_CC = struct(
    c_compile_action_name = _C_COMPILE_ACTION_NAME,
    cpp_compile_action_name = _CPP_COMPILE_ACTION_NAME,
    follow_attributes = ["_cc_toolchain"],
    toolchains_aspects = [],
    toolchain_target = _get_cc_toolchain_target,
)
