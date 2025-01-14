package(default_visibility = ["//simple_java:__pkg__"])

java_library(
    name = "nested",
    srcs = glob(["**/NestedClass.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java_and_deps/deps/top_level_lib_2",
    ],
)

java_library(
    name = "nested2",
    srcs = glob(["**/NestedClass2.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java_and_deps/deps/transitive_dep_lib",
    ],
)
