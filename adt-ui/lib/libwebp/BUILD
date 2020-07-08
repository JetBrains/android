load("//tools/adt/idea/studio:studio.bzl", "studio_data")

filegroup(
    name = "libwebp",
    srcs = glob([
        "mac/*.dylib",
        "win/*.dll",
        "linux/*.so",
    ]),
    visibility = ["//visibility:public"],
)

studio_data(
    name = "libwebp-bundle",
    files_linux = glob(["linux/*.so"]),
    files_mac = glob(["mac/*.dylib"]),
    files_win = glob(["win/*.dll"]),
    mappings = {
        "tools/adt/idea/adt-ui/lib/libwebp/linux/": "",
        "tools/adt/idea/adt-ui/lib/libwebp/mac/": "",
        "tools/adt/idea/adt-ui/lib/libwebp/win/": "",
    },
    visibility = ["//visibility:public"],
)
