load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)
load("//tools/base/bazel:kotlin.bzl", "kt_jvm_library")

package(
    default_visibility = ["//visibility:private"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":lib",
        ":sample1",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

kt_jvm_library(
    name = "sample1",
    srcs = glob(["java/com/example/sample1/**/*.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//tools/adt/idea/aswb/aswb/testdata/projects/kotlin1/java/com/example/sample1/nested",
        "@maven//:com.google.guava.guava",
    ],
)

kt_jvm_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/**/*.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com.google.guava.guava",
    ],
)
