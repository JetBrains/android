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
        ":libchilddep",
    ],
)

java_library(
    name = "libchilddep",
    srcs = ["LibChildDep.java"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "libuser",
    srcs = ["LibUser.java"],
    visibility = ["//visibility:public"],
    deps = [
        ":libchild",
        ":libchildimpl",
    ],
)
