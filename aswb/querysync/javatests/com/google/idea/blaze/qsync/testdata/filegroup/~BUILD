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

java_library(
    name = "filegroup",
    srcs = [":sources"],
    deps = ["//tools/vendor/google/aswb/plugin_api/maven:guava"],
)
