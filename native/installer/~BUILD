load("//tools/base/bazel:utils.bzl", "dir_archive")

filegroup(
    name = "uninstaller",
    srcs = ["win/builds/uninstall.exe"],
    visibility = ["//visibility:public"],
)

dir_archive(
    name = "android-studio-bundle-data",
    dir = "tools/adt/idea/native/installer/win",
    files = glob(
        include = ["win/**"],
        exclude = ["win/version"],
    ),
    stamp = "version",
    visibility = ["//visibility:public"],
)
