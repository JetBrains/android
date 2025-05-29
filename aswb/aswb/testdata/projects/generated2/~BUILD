load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(
    default_visibility = ["//src:__subpackages__"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":user",
        ":generated_src",
        ":generated",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

java_library(
    name = "user",
    srcs = glob(["java/**/*.java"]),
    deps = [":generated"],
)

copy_file(
    name = "generated_src",
    src = "Generated.java_",
    out = "src/com/example/Generated.java",
)

java_library(
    name = "generated",
    srcs = [":generated_src"],
)
