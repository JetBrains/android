load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)
load("//tools/base/bazel:kotlin.bzl", "kt_jvm_library")

package(
    default_visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/kotlin1:__pkg__"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":nested",
        ":nested2",
    ],
    visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/kotlin1:__pkg__"],
)

kt_jvm_library(
    name = "nested",
    srcs = glob(["**/NestedClass.kt"]),
    visibility = ["//visibility:public"],
    deps = ["@maven//:com.google.guava.guava"],
)

kt_jvm_library(
    name = "nested2",
    srcs = glob(["**/NestedClass2.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com.google.guava.guava",
    ],
)
