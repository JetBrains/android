package(default_visibility = ["//visibility:public"])

cc_library(
    name = "layout_inspector_test_support",
    srcs = [
        "layoutinspector/boxes.cc",
        "layoutinspector/single.cc",
    ],
    copts = [
        "-DSK_USER_CONFIG_HEADER=\\\"SkUserConfig.h\\\"",
        "-UFORTIFY_SOURCE",
        "-DATRACE_TAG=ATRACE_TAG_VIEW",
        "-DSKIA_IMPLEMENTATION=1",
        "-DSK_PRINT_CODEC_MESSAGES",
        "-D_FORTIFY_SOURCE=1",
    ],
    linkstatic = 1,
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "@freetype_repo//:libft2",
        "@libpng_repo//:libpng",
        "@prebuilt_skia//:libskia",
    ],
    alwayslink = 1,
)

cc_binary(
    name = "liblayout_inspector_test_support.dylib",
    linkshared = 1,
    tags = [
        "no_linux",
        "no_windows",
    ],
    deps = [
        ":layout_inspector_test_support",
    ],
)

cc_binary(
    name = "liblayout_inspector_test_support.so",
    linkshared = 1,
    tags = [
        "no_mac",
        "no_windows",
    ],
    deps = [
        ":layout_inspector_test_support",
    ],
)

cc_binary(
    name = "layout_inspector_test_support.dll",
    linkshared = 1,
    tags = [
        "no_linux",
        "no_mac",
    ],
    deps = [
        ":layout_inspector_test_support",
    ],
)
