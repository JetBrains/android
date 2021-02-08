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

# Data that gets bundled in studio
studio_data(
    name = "sample-data-bundle",
    files = glob(["sampleData/**"]),
    mappings = {
        "tools/adt/idea/android/lib/": "",
    },
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "android-sdk-tools-jps",
    jars = ["jarutils.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "spantable",
    jars = ["spantable.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "instantapps-api",
    jars = ["instantapps-api-1.8.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "pepk",
    jars = ["pepk.jar"],
    visibility = ["//visibility:public"],
)
