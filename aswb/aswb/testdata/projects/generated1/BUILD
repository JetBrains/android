load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_pkg//pkg:pkg.bzl", "pkg_zip")
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
        ":generated_jar",
        ":generated_jar_producer",
        ":generated_srcjar",
        ":library_exporting_generated_jar",
        ":library_from_generated_srcjar",
        ":sample1_library",
        ":sample1_copy",
        ":sample2_copy",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

java_library(
    name = "consumer",
    srcs = glob(["java/com/example/consumer/*.java"]),
    deps = [
        ":library_exporting_generated_jar",
        ":library_from_generated_srcjar",
    ],
)

# Targets to produce a generated .jar consumed by a `java_import` rule and
# re-exported as a `java_library` target `:library_exporting_generated_jar`.

java_library(
    name = "library_exporting_generated_jar",
    exports = [":generated_jar"],
)

java_import(
    name = "generated_jar",
    jars = [":generated_jar_producer"],
)

copy_file(
    name = "generated_jar_producer",
    src = ":sample1_library",
    out = "generated.jar",
)

java_library(
    name = "sample1_library",
    srcs = [
        "java/com/example/consumer/Sample1Consumer.java",
        ":sample1_copy",
    ],
)

copy_file(
    name = "sample1_copy",
    src = "java/com/example/srcjar/Sample1.java_",
    out = "java/com/example/srcjar/Sample1.java",
)

# Targets to produce a generated .srcjar consumed by a `java_library` target
# `:library_from_generated_srcjar`.

java_library(
    name = "library_from_generated_srcjar",
    srcs = [":generated_srcjar"],
    visibility = ["//visibility:public"],
)

pkg_zip(
    name = "generated_srcjar",
    srcs = [
        ":sample2_copy",
    ],
    out = "generated.srcjar",
)

copy_file(
    name = "sample2_copy",
    src = "java/com/example/srcjar/Sample2.java_",
    out = "java/com/example/srcjar/Sample2.java",
)
