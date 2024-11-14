load("//tools/base/bazel:utils.bzl", "dir_archive")

filegroup(
    name = "annotations",
    srcs = glob(["**"]),
    visibility = ["//visibility:public"],
)

dir_archive(
    name = "androidAnnotations",
    dir = "tools/adt/idea/android/annotations",
    ext = "jar",
    files = glob(
        include = ["**"],
        exclude = ["BUILD"],
    ),
    visibility = ["//visibility:public"],
)
