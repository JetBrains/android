load("//tools/adt/idea/studio:studio.bzl", "studio_data")

filegroup(
    name = "sampleData",
    srcs = glob(["sampleData/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "licenses",
    srcs = glob(["licenses/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "sample-data",
    srcs = glob(["sampleData/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "apks",
    srcs = glob(["apks/*.apk"]),
    visibility = ["//visibility:public"],
)

# Data that gets bundled in studio
studio_data(
    name = "sample-data-bundle",
    files = glob(["sampleData/**"]),
    mappings = {
        "tools/adt/idea/android/lib/": "",
    },
    visibility = ["//visibility:public"],
)

studio_data(
    name = "apks-bundle",
    files = glob(["apks/*.apk"]),
    mappings = {
        "tools/adt/idea/android/lib/": "",
    },
    visibility = ["//visibility:public"],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "instantapps-api",
    jars = ["instantapps-api-1.8.jar"],
    visibility = ["//visibility:public"],
)
