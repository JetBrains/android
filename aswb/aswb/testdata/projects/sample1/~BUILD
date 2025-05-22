load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(default_visibility = ["//visibility:private"])

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

java_library(
    name = "sample1",
    srcs = glob(["java/com/example/sample1/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//tools/adt/idea/aswb/aswb/testdata/projects/sample1/java/com/example/sample1/nested",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
    ],
)
