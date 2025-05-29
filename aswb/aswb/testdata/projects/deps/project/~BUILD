load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

java_library(
    name = "project",
    srcs = ["java/com/example/project/Class1.java"],
    deps = [
        "//tools/adt/idea/aswb/aswb/testdata/projects/deps/external",
        "//tools/adt/idea/aswb/aswb/testdata/projects/deps/external:external_gensrcjar",
        "//tools/adt/idea/aswb/aswb/testdata/projects/deps/external:external_srcjar",
    ],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":project",
    ],
    external_sources = ["//tools/adt/idea/aswb/aswb/testdata/projects/deps/external:sources"],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)
