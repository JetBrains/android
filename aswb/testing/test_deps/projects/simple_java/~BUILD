package(default_visibility = ["//visibility:private"])

java_library(
    name = "sample",
    srcs = glob(["java/com/example/sample/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//simple_java/java/com/example/sample/nested",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
    ],
)
