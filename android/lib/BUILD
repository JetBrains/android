java_import(
    name = "commons-io-2.4",
    jars = ["commons-io-2.4.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "juniversalchardet-1.0.3",
    jars = ["juniversalchardet-1.0.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "antlr4-runtime-4.5.3",
    jars = ["antlr4-runtime-4.5.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "asm-5.0.3",
    jars = ["asm-5.0.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "asm-analysis-5.0.3",
    jars = ["asm-analysis-5.0.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "asm-tree-5.0.3",
    jars = ["asm-tree-5.0.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "jarutils",
    jars = ["jarutils.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "spantable",
    jars = ["spantable.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "jgraphx-3.4.0.1",
    jars = ["jgraphx-3.4.0.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "libwebp",
    jars = ["libwebp.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "baksmali-2.2.1",
    jars = ["baksmali-2.2.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "dexlib2-2.2.1",
    jars = ["dexlib2-2.2.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "guava-testlib-21.0",
    jars = ["guava-testlib-21.0.jar"],
    tags = ["managed"],
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

java_import(
    name = "util-2.2.1",
    jars = ["util-2.2.1.jar"],
    tags = ["managed"],
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
