load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(
    default_visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/gensrcjars:__subpackages__"],
)

java_library(
    name = "consumer",
    srcs = glob(["*.java"]),
    deps = [
        "//tools/adt/idea/aswb/aswb/testdata/projects/gensrcjars/java/com/example/jar1",
        "//tools/adt/idea/aswb/aswb/testdata/projects/gensrcjars/java/com/example/jar2",
        "@maven//:com.google.guava.guava",
    ],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":consumer",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/adt/idea/aswb/aswb/testdata/projects/gensrcjars:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)
