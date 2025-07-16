package(default_visibility = ["//visibility:private"])

java_library(
    name = "transitive_dep_lib2",
    srcs = glob(["java/com/example/transitive_dep_lib2/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java_and_deps/deps/deep_transitive_dep_lib",
    ],
)
