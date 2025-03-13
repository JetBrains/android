load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(
    default_visibility = ["//visibility:public"],
)

kt_jvm_library(
    name = "nested",
    srcs = glob(["**/NestedClass.kt"]),
    deps = [
        "//simple_kotlin/external",
    ],
)

kt_jvm_library(
    name = "nested2",
    srcs = glob(["**/NestedClass2.kt"]),
    deps = [
        "//simple_kotlin/external:external2",
    ],
)
