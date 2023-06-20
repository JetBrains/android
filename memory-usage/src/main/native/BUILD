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
        "tools/adt/idea/memory-usage/src/main/native/linux/": "native/",
        "tools/adt/idea/memory-usage/src/main/native/mac/": "native/",
        "tools/adt/idea/memory-usage/src/main/native/mac_arm/": "native/",
        "tools/adt/idea/memory-usage/src/main/native/win/": "native/",
    },
    visibility = ["//visibility:public"],
)

cc_library(
    name = "lightweight_heap_traverse",
    srcs = [
        "lightweightHeapTraverse.cpp",
        "lightweightHeapTraverse.h",
    ],
    copts = [
        "-O2",
        "-shared",
    ] + select({
        "//tools/base/bazel:darwin": [
            "-Iprebuilts/studio/jdk/mac/Contents/Home/include",
        ],
        "//tools/base/bazel:windows": [
            "-Iprebuilts/studio/jdk/win64/include",
        ],
        "//conditions:default": [
            "-Iprebuilts/studio/jdk/linux/include",
            "-Iprebuilts/studio/jdk/linux/include/linux",
        ],
    }),
    linkstatic = True,
    deps = ["//prebuilts/studio/jdk:jni_headers"],
    alwayslink = True,
)

cc_binary(
    name = "liblightweight_heap_traverse.so",
    linkopts = ["-Wl,--strip-all"],
    linkshared = True,
    stamp = 0,
    tags = [
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":lightweight_heap_traverse",
    ],
)

cc_binary(
    name = "liblightweight_heap_traverse.dylib",
    linkshared = True,
    stamp = 0,
    tags = [
        "no_linux",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":lightweight_heap_traverse",
    ],
)

cc_binary(
    name = "lightweight_heap_traverse.dll",
    linkshared = True,
    stamp = 0,
    tags = [
        "no_linux",
        "no_mac",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":lightweight_heap_traverse",
    ],
)

filegroup(
    name = "liblightweight_heap_traverse",
    srcs = select({
        "@platforms//os:linux": [":liblightweight_heap_traverse.so"],
        "@platforms//os:macos": [":liblightweight_heap_traverse.dylib"],
        "@platforms//os:windows": [":lightweight_heap_traverse.dll"],
    }),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "liblightweight_heap_traverse.prebuilt",
    srcs = select({
        "@platforms//os:linux": glob(["linux/*"]),
        ":macos": glob(["mac/*"]),
        ":macos_arm": glob(["mac_arm/*"]),
        "@platforms//os:windows": glob(["win/*"]),
    }),
    visibility = ["//visibility:public"],
)

genrule(
    name = "update_liblightweight_heap_traverse",
    srcs = [
        ":liblightweight_heap_traverse",
        "//tools/base/repository:find_workspace",
    ],
    outs = ["unused_out"],
    cmd = "touch $(location unused_out) && cp $(location liblightweight_heap_traverse) " +
          "`cat $(location //tools/base/repository:find_workspace)`/tools/adt/idea/memory-usage/src/main/native/" +
          select({
              "@platforms//os:linux": "linux",
              ":macos": "mac",
              ":macos_arm": "mac_arm",
              "@platforms//os:windows": "win",
          }),
    local = True,
    tags = ["manual"],
)
