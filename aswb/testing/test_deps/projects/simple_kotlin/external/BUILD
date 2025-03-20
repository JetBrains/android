load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(
    default_visibility = ["//visibility:public"],
)

kt_jvm_library(
    name = "external",
    srcs = glob(["com/example/external/lib/*.kt"]),
)

kt_jvm_library(
    name = "external2",
    srcs = glob(["com/example/external/lib2/*.kt"]),
)

kt_jvm_library(
    name = "external3",
    srcs = glob(["com/example/external/lib3/*.kt"]),
)
