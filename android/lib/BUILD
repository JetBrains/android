# managed by go/iml_to_build
java_import(
    name = "asm-5.0.3",
    jars = ["asm-5.0.3.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "asm-analysis-5.0.3",
    jars = ["asm-analysis-5.0.3.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "asm-tree-5.0.3",
    jars = ["asm-tree-5.0.3.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "jarutils",
    jars = ["jarutils.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "spantable",
    jars = ["spantable.jar"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "androidWidgets",
    srcs = glob(["androidWidgets/**"]),
    visibility = ["//visibility:public"],
)

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
    name = "bundled-libs",
    srcs = glob([
        # TODO: Figure out why we need to bundle these if they
        # are not needed to compile.
        "commons-compress-1.8.1.jar",
        "javawriter-2.2.1.jar",
        "androidWidgets/**",
    ]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "sample-data",
    srcs = glob(["sampleData/**"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "instantapps-api-1.8",
    jars = ["instantapps-api-1.8.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "pepk",
    jars = ["pepk.jar"],
    visibility = ["//visibility:public"],
)
