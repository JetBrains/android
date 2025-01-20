package(default_visibility = ["//visibility:private"])

java_library(
    name = "sample",
    srcs = glob(["java/com/example/sample/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//java_and_deps/deps/top_level_lib_1",
        "//java_and_deps/project/java/com/example/sample/nested",
    ],
)

java_library(
    name = "sample_no_ide",
    srcs = glob(["java/com/example/sample/*.java"]),
    tags = ["no-ide"],
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//java_and_deps/deps/no_ide",
        "//java_and_deps/deps/top_level_lib_1",
        "//java_and_deps/project/java/com/example/sample/nested",
    ],
)

java_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java_and_deps/deps/top_level_lib_2",
    ],
)
