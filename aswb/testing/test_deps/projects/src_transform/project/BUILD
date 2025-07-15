load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "src_transform",
    srcs = ["java/com/example/UseTestClass.java"],
    deps = [
        "//src_transform/deps:src_transform",
        "@maven//:com_google_guava_guava",
    ],
)
