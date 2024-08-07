"""Test module for the purpose of including something in a BUILD file."""

def my_java_library(name):
    native.java_library(
        name = name,
        srcs = native.glob(["*.java"]),
        deps = [],
    )
