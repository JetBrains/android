load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//navigation_to_source_java:__subpackages__"])

java_library(
    name = "project",
    srcs = ["java/com/example/project/Class1.java"],
    deps = [
        "//navigation_to_source_java/external",
        "//navigation_to_source_java/external:external_gensrcjar",
        "//navigation_to_source_java/external:external_srcjar",
    ],
)
