load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_java//java:defs.bzl", "java_import", "java_library")
load("@rules_pkg//pkg:pkg.bzl", "pkg_zip")

package(
    default_visibility = ["//visibility:public"],
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
