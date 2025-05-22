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
        ":consumer",
        ":generated",
        ":generated.srcjar",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

java_library(
    name = "consumer",
    srcs = glob(["java/com/example/*.java"]),
    deps = [":generated"],
)

# Targets to produce a generated .srcjar

java_library(
    name = "generated",
    srcs = [":generated.srcjar"],
)

genrule(
    name = "generated.srcjar",
    srcs = ["java/com/example/Sample1.java_"],
    outs = ["sample1.srcjar"],
    cmd = " ".join([
        "WORKING_DIR=generated.srcjar_tmp;",
        "rm -rf $$WORKING_DIR;",
        "mkdir $$WORKING_DIR;",
        "cat $< > $$WORKING_DIR/Sample1.java;",
        "zip -qX $@ $$WORKING_DIR/Sample1.java",
    ]),
)
