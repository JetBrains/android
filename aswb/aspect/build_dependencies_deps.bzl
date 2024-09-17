"""Loads and re-exports dependencies of build_dependencies.bzl to support different versions of bazel"""

load(
    "@bazel_tools//tools/build_defs/cc:action_names.bzl",
    _CPP_COMPILE_ACTION_NAME = "CPP_COMPILE_ACTION_NAME",
    _C_COMPILE_ACTION_NAME = "C_COMPILE_ACTION_NAME",
)

# re-export these with their original names:
CPP_COMPILE_ACTION_NAME = _CPP_COMPILE_ACTION_NAME
C_COMPILE_ACTION_NAME = _C_COMPILE_ACTION_NAME

ZIP_TOOL_LABEL = "@bazel_tools//tools/zip:zipper"

ANDROID_IDE_INFO = None
