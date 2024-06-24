load("//tools/adt/idea/studio:studio.bzl", "searchable_options")

searchable_options(
    name = "searchable-options",
    files = glob(["*/**"]),
    strip_prefix = "tools/adt/idea/searchable-options/",
    visibility = ["//visibility:public"],
)

py_test(
    name = "searchable_options_test",
    srcs = [
        "searchable_options_test.py",
        "update_searchable_options.py",
    ],
    data = [
        "//tools/adt/idea/studio:android-studio.linux.zip",
        "//tools/adt/idea/studio:android-studio.mac.zip",
        "//tools/adt/idea/studio:android-studio.plugin.lst",
    ],
    imports = ["."],
    tags = [
        "block_network",
        "noci:studio-win",
    ],
)

py_binary(
    name = "update_searchable_options",
    srcs = ["update_searchable_options.py"],
    data = [
        "//tools/adt/idea/studio:android-studio.linux.zip",
        "//tools/adt/idea/studio:android-studio.mac.zip",
        "//tools/adt/idea/studio:android-studio.plugin.lst",
    ],
    tags = [
        "block_network",
        "noci:studio-win",
    ],
)
