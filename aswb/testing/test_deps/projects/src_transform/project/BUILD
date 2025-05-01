load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "src_transform",
    srcs = [":transformed_source"],
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

genrule(
    name = "transformed_source",
    srcs = ["java/com/example/TestClass.java"],
    outs = ["gen/com/example/TestClass.java"],
    cmd = "cp $< $@",
    tags = ["ij-ignore-source-transform"],
)
