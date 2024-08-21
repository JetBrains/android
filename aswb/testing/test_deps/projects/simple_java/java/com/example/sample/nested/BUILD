package(default_visibility = ["//simple_java:__pkg__"])

java_library(
    name = "nested",
    srcs = glob(["**/NestedClass.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "nested2",
    srcs = glob(["**/NestedClass2.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)
