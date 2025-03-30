load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)
load(
    "//tools/adt/idea/aswb/aswb/testdata/projects/gensrcjars:gen_srcjar.bzl",
    "gen_srcjar",
)

package(
    default_visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/gensrcjars:__subpackages__"],
)

targets = gen_srcjar(
    name = "jar2",
    src = "Jar2Class.java_",
    package_dir = "com/example/jar2",
)

test_project_package(
    name = "test_data",
    all_targets = targets,
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/adt/idea/aswb/aswb/testdata/projects/gensrcjars:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)
