"""Loads and re-exports dependencies of build_dependencies.bzl to support different versions of bazel"""

load(":build_dependencies_android_deps.bzl", _IDE_ANDROID = "IDE_ANDROID")
load(":build_dependencies_cc_deps.bzl", _IDE_CC = "IDE_CC")
load(":build_dependencies_java_deps.bzl", _IDE_JAVA = "IDE_JAVA")
load(":build_dependencies_java_proto_deps.bzl", _IDE_JAVA_PROTO = "IDE_JAVA_PROTO")
load(":build_dependencies_kotlin_deps.bzl", _IDE_KOTLIN = "IDE_KOTLIN")

IDE_ANDROID = _IDE_ANDROID
IDE_CC = _IDE_CC
IDE_JAVA = _IDE_JAVA
IDE_JAVA_PROTO = _IDE_JAVA_PROTO
IDE_KOTLIN = _IDE_KOTLIN

ZIP_TOOL_LABEL = "@bazel_tools//tools/zip:zipper"
