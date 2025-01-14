package(default_visibility = ["//visibility:private"])

java_library(
    name = "top_level_lib_1",
    srcs = glob(["java/com/example/top_level_lib_1/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java_and_deps/deps/transitive_dep_lib",
    ],
)
