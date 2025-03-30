load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(default_visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/sample1:__pkg__"])

test_project_package(
    name = "test_data",
    all_targets = [
        ":nested",
        ":nested2",
    ],
    visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/sample1:__pkg__"],
)

java_library(
    name = "nested",
    srcs = glob(["**/NestedClass.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "nested2",
    srcs = glob(["**/NestedClass2.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com.google.guava.guava",
    ],
)
