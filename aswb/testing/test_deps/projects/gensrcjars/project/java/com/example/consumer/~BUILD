load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "consumer",
    srcs = glob(["*.java"]),
    deps = [
        "//gensrcjars/project/java/com/example/jar1",
        "//gensrcjars/project/java/com/example/jar2",
        "@maven//:com_google_guava_guava",
    ],
)
