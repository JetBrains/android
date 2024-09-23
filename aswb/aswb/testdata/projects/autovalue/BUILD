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
        ":autoovalue",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

java_library(
    name = "autoovalue",
    srcs = glob(["java/**/*.java"]),
    deps = [
        "//tools/adt/idea/aswb/third_party/java/auto_value",
    ],
)
