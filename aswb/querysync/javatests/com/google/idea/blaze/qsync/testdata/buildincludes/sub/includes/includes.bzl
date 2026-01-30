"""Test module for the purpose of including something in a BUILD file."""

load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

def my_java_library(name):
    java_library_for_test_data(
        name = name,
        srcs = native.glob(["*.java"]),
        deps = [],
    )
