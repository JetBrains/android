package(default_visibility = ["//visibility:private"])

java_library(
    name = "lib1",
    srcs = glob(["java/com/example/lib1/*.java"]),
    visibility = ["//visibility:public"],
)

java_library(
    name = "reexported_lib1",
    visibility = ["//visibility:public"],
    exports = [":lib1"],
)
