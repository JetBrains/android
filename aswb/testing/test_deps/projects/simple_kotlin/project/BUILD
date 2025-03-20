load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(
    default_visibility = ["//visibility:public"],
)

kt_jvm_library(
    name = "project",
    srcs = glob(["java/com/example/*.kt"]),
    deps = [
        ":lib",
        "//simple_kotlin/external",
        "//simple_kotlin/project/java/com/example/nested",
    ],
)

kt_jvm_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/**/*.kt"]),
    deps = [
        "//simple_kotlin/external:external3",
    ],
)
