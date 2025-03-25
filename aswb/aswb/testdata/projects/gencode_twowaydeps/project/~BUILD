load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)
load("//tools/base/bazel:kotlin.bzl", "kt_jvm_library")

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
        "//tools/adt/idea/aswb/aswb/testdata/projects/gencode_twowaydeps/external:interface",
    ],
)

java_library(
    name = "java",
    srcs = [
        "java/com/example/project/OuterClass.java",
    ],
    deps = [
        "//tools/adt/idea/aswb/aswb/testdata/projects/gencode_twowaydeps/external:annotation",
        "//tools/adt/idea/aswb/aswb/testdata/projects/gencode_twowaydeps/external:interface",
    ],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":project",
        ":java",
    ],
    external_sources = ["//tools/adt/idea/aswb/aswb/testdata/projects/gencode_twowaydeps/external:sources"],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)
