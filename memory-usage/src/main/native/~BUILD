load("//tools/adt/idea/studio:studio.bzl", "studio_data")

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
        "//tools/base/bazel/platforms:macos-x86_64": [
            "-Iprebuilts/studio/jdk/mac/Contents/Home/include",
        ],
        "@platforms//os:windows": [
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
    target_compatible_with = ["@platforms//os:linux"],
    visibility = ["//visibility:public"],
    deps = [
        ":lightweight_heap_traverse",
    ],
)

cc_binary(
    name = "liblightweight_heap_traverse.dylib",
    linkshared = True,
    stamp = 0,
    target_compatible_with = ["@platforms//os:macos"],
    visibility = ["//visibility:public"],
    deps = [
        ":lightweight_heap_traverse",
    ],
)

cc_binary(
    name = "lightweight_heap_traverse.dll",
    linkshared = True,
    stamp = 0,
    target_compatible_with = ["@platforms//os:windows"],
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
        "//tools/base/bazel/platforms:macos-x86_64": glob(["mac/*"]),
        "//tools/base/bazel/platforms:macos-arm64": glob(["mac_arm/*"]),
        "@platforms//os:windows": glob(["win/*"]),
    }),
    visibility = ["//visibility:public"],
)

# bazel run :update_liblightweight_heap_traverse
sh_binary(
    name = "update_liblightweight_heap_traverse",
    srcs = ["//tools/base/bazel/shell:workspace-cp.sh"],
    args = [
        "$(execpath liblightweight_heap_traverse)",
    ] + select({
        "@platforms//os:linux": ["tools/adt/idea/memory-usage/src/main/native/linux"],
        "//tools/base/bazel/platforms:macos-x86_64": ["tools/adt/idea/memory-usage/src/main/native/mac"],
        "//tools/base/bazel/platforms:macos-arm64": ["tools/adt/idea/memory-usage/src/main/native/mac_arm"],
        "@platforms//os:windows": ["tools/adt/idea/memory-usage/src/main/native/win"],
    }),
    data = [":liblightweight_heap_traverse"],
)
