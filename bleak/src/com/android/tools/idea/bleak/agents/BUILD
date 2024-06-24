cc_library(
    name = "jnibleakhelper",
    srcs = [
        "jniBleakHelper.cpp",
        "jniBleakHelper.h",
    ],
    copts = [
        "-fPIC",
        "-shared",
    ] + select({
        "@platforms//os:macos": [
            "-Iprebuilts/studio/jdk/mac/Contents/Home/include",
        ],
        "//conditions:default": [
            "-Iprebuilts/studio/jdk/linux/include",
            "-Iprebuilts/studio/jdk/linux/include/linux",
        ],
    }),
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
    ],
)
