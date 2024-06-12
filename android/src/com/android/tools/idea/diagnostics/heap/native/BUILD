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
        "tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/linux/": "native/",
        "tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/mac/": "native/",
        "tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/mac_arm/": "native/",
        "tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/win/": "native/",
    },
    visibility = ["//visibility:public"],
)

cc_library(
    name = "jni_object_tagger",
    srcs = [
        "jniObjectTagger.cpp",
        "jniObjectTagger.h",
    ],
    copts = [
        "-O2",
        "-shared",
    ] + select({
        "@platforms//os:macos": [
            "-Iprebuilts/studio/jdk/mac/Contents/Home/include",
        ],
        "@platforms//os:windows": [
            "-Iprebuilts/studio/jdk/win64/include",
        ],
        "@platforms//os:linux": [
            "-Iprebuilts/studio/jdk/linux/include",
            "-Iprebuilts/studio/jdk/linux/include/linux",
        ],
    }),
    linkstatic = True,
    deps = ["//prebuilts/studio/jdk:jni_headers"],
    alwayslink = True,
)

cc_binary(
    name = "libjni_object_tagger.so",
    linkopts = ["-Wl,--strip-all"],
    linkshared = True,
    stamp = 0,
    target_compatible_with = ["@platforms//os:linux"],
    visibility = ["//visibility:public"],
    deps = [
        ":jni_object_tagger",
    ],
)

cc_binary(
    name = "libjni_object_tagger.dylib",
    linkshared = True,
    stamp = 0,
    target_compatible_with = ["@platforms//os:macos"],
    visibility = ["//visibility:public"],
    deps = [
        ":jni_object_tagger",
    ],
)

cc_binary(
    name = "jni_object_tagger.dll",
    linkshared = True,
    stamp = 0,
    target_compatible_with = ["@platforms//os:windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":jni_object_tagger",
    ],
)

filegroup(
    name = "libjni_object_tagger",
    srcs = select({
        "@platforms//os:linux": [":libjni_object_tagger.so"],
        "@platforms//os:macos": [":libjni_object_tagger.dylib"],
        "@platforms//os:windows": [":jni_object_tagger.dll"],
    }),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "libjni_object_tagger.prebuilt",
    srcs = select({
        "@platforms//os:linux": glob(["linux/*"]),
        ":macos": glob(["mac/*"]),
        ":macos_arm": glob(["mac_arm/*"]),
        "@platforms//os:windows": glob(["win/*"]),
    }),
    visibility = ["//visibility:public"],
)

# bazel run :update_libjni_object_tagger
sh_binary(
    name = "update_libjni_object_tagger",
    srcs = ["//tools/base/bazel/shell:workspace-cp.sh"],
    args = [
        "$(execpath libjni_object_tagger)",
    ] + select({
        "@platforms//os:linux": ["tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/linux"],
        ":macos": ["tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/mac"],
        ":macos_arm": ["tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/mac_arm"],
        "@platforms//os:windows": ["tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/win"],
    }),
    data = [":libjni_object_tagger"],
)
