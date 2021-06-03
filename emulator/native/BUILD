load("//tools/adt/idea/studio:studio.bzl", "studio_data")

studio_data(
    name = "native_lib",
    files_linux = glob(["linux/*"]),
    files_mac = glob(["mac/*"]),
    files_mac_arm = glob(["mac_arm/*"]),
    files_win = glob(["win/*"]),
    mappings = {
        "tools/adt/idea/emulator/native/linux/": "native/",
        "tools/adt/idea/emulator/native/mac/": "native/",
        "tools/adt/idea/emulator/native/mac_arm/": "native/",
        "tools/adt/idea/emulator/native/win/": "native/",
    },
    visibility = ["//visibility:public"],
)
