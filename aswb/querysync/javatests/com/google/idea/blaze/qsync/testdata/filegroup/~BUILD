load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

filegroup(
    name = "nested_filegroup",
    srcs = ["TestSubFileGroupSource.java"],
)

filegroup(
    name = "sources",
    srcs = [
        "TestFileGroupSource.java",
        ":nested_filegroup",
    ],
)

java_library_for_test_data(
    name = "filegroup",
    srcs = [":sources"],
    deps = ["//tools/vendor/google/aswb/plugin_api/maven:guava"],
)
