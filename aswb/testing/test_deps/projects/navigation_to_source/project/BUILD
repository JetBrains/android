load("@rules_java//java:defs.bzl", "java_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_visibility = ["//navigation_to_source:__subpackages__"])

java_library(
    name = "java",
    srcs = ["java/com/example/project/Class1.java"],
    deps = [
        "//navigation_to_source/external:external_gensrcjar",
        "//navigation_to_source/external:external_java",
        "//navigation_to_source/external:external_kt",
        "//navigation_to_source/external:external_srcjar",
        "//navigation_to_source/external:kt_external_gensrcjar",
        "//navigation_to_source/external:kt_external_srcjar",
    ],
)

kt_jvm_library(
    name = "kt",
    srcs = ["kt/com/example/project/Class2.kt"],
    deps = [
        "//navigation_to_source/external:external_gensrcjar",
        "//navigation_to_source/external:external_java",
        "//navigation_to_source/external:external_kt",
        "//navigation_to_source/external:external_srcjar",
        "//navigation_to_source/external:kt_external_gensrcjar",
        "//navigation_to_source/external:kt_external_srcjar",
    ],
)
