package(default_visibility = ["//visibility:private"])

java_library(
    name = "lib",
    srcs = ["Lib.java"],
    visibility = ["//visibility:public"],
    deps = [
    ],
)

java_library(
    name = "libchildimpl",
    srcs = ["LibChildImpl.java"],
    visibility = ["//visibility:public"],
    deps = [
        ":libchild",
    ],
)

java_library(
    name = "libchild",
    srcs = ["LibChild.java"],
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
    ],
)
