load("//tools/adt/idea/studio:studio.bzl", "studio_data")

config_setting(
    name = "macos",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:macos",
    ],
)

config_setting(
    name = "macos_arm",
    constraint_values = [
        "@platforms//cpu:arm64",
        "@platforms//os:macos",
    ],
)

studio_data(
    name = "native_lib",
    files_linux = glob(["linux/*"]),
    files_mac = glob(["mac/*"]),
    files_mac_arm = glob(["mac_arm/*"]),
    files_win = glob(["win/*"]),
    mappings = {
        "tools/adt/idea/streaming/native/linux/": "native/",
        "tools/adt/idea/streaming/native/mac/": "native/",
        "tools/adt/idea/streaming/native/mac_arm/": "native/",
        "tools/adt/idea/streaming/native/win/": "native/",
    },
    visibility = ["//visibility:public"],
)

cc_library(
    name = "image_converter",
    srcs = ["ImageConverter.c"],
    copts = ["-O2"] + select({
        "@platforms//cpu:x86_64": ["-mmovbe"],
        "//conditions:default": [],
    }),
    defines = ["NDEBUG"],
    linkstatic = True,
    deps = ["//prebuilts/studio/jdk:jni_headers"],
    alwayslink = True,
)

cc_binary(
    name = "libimage_converter.so",
    linkopts = ["-Wl,--strip-all"],
    linkshared = True,
    stamp = 0,
    tags = [
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":image_converter",
    ],
)

cc_binary(
    name = "libimage_converter.dylib",
    linkshared = True,
    stamp = 0,
    tags = [
        "no_linux",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":image_converter",
    ],
)

cc_binary(
    name = "image_converter.dll",
    linkshared = True,
    stamp = 0,
    tags = [
        "no_linux",
        "no_mac",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":image_converter",
    ],
)

filegroup(
    name = "libimage_converter",
    srcs = select({
        "@platforms//os:linux": [":libimage_converter.so"],
        "@platforms//os:macos": [":libimage_converter.dylib"],
        "@platforms//os:windows": [":image_converter.dll"],
    }),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "libimage_converter.prebuilt",
    srcs = select({
        "@platforms//os:linux": glob(["linux/*"]),
        ":macos": glob(["mac/*"]),
        ":macos_arm": glob(["mac_arm/*"]),
        "@platforms//os:windows": glob(["win/*"]),
    }),
    visibility = ["//visibility:public"],
)

genrule(
    name = "update_libimage_converter",
    srcs = [
        ":libimage_converter",
        "//tools/base/repository:find_workspace",
    ],
    outs = ["unused_out"],
    cmd = "touch $(location unused_out) && cp $(location libimage_converter) " +
          "`cat $(location //tools/base/repository:find_workspace)`/tools/adt/idea/streaming/native/" +
          select({
              "@platforms//os:linux": "linux",
              ":macos": "mac",
              ":macos_arm": "mac_arm",
              "@platforms//os:windows": "win",
          }),
    local = True,
    tags = ["manual"],
)
