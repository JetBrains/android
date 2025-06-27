load("@rules_java//java:defs.bzl", "java_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

# Ideally, we would have a single kt_jvm_library rule here, and all the source
# would be in Kotlin. But since the Kotlin support we load above does not
# run annotations processors on Kotlin code, we have the annotated code in Java.

kt_jvm_library(
    name = "project",
    srcs = [
        "java/com/example/project/KotlinClass.kt",
    ],
    deps = [
        ":java",
        "//gencode_twowaydeps/external:interface",
    ],
)

java_library(
    name = "java",
    srcs = [
        "java/com/example/project/OuterClass.java",
    ],
    deps = [
        "//gencode_twowaydeps/external:annotation",
        "//gencode_twowaydeps/external:interface",
    ],
)
