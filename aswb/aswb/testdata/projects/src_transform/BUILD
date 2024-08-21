load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(default_visibility = ["//visibility:private"])

test_project_package(
    name = "test_data",
    all_targets = [
        ":src_transform",
        ":transformed_source",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

java_library(
    name = "src_transform",
    srcs = [":transformed_source"],
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com.google.guava.guava",
    ],
)

genrule(
    name = "transformed_source",
    srcs = ["java/com/example/TestClass.java"],
    outs = ["gen/com/example/TestClass.java"],
    cmd = "cp $< $@",
    tags = ["ij-ignore-source-transform"],
)
