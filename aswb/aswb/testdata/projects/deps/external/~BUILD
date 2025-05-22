load("@rules_pkg//pkg:mappings.bzl", "strip_prefix")
load("@rules_pkg//pkg:pkg.bzl", "pkg_zip")
load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "external_sources_package",
)

java_library(
    name = "external",
    srcs = ["java/com/example/external/ExternalJavaSource.java"],
    visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/deps:__subpackages__"],
)

external_sources_package(
    name = "sources",
    visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/deps:__subpackages__"],
)

# Build the srcjar as a library:
java_library(
    name = "build_from_srcjar",
    srcs = ["java/com/example/external/external.srcjar"],
)

# Re-import the jar from the above rule, and set srcjar so that IDE code can
# find the sources:
java_import(
    name = "external_srcjar",
    jars = [":libbuild_from_srcjar.jar"],
    srcjar = "java/com/example/external/external.srcjar",
    visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/deps:__subpackages__"],
)

# Generates a srcjar. The java source root within the srcjar is not the jar file
# root, since it is derived from the full workspace path to the source files.
# This is what we want to ensure that such sources can still be found by the IDE
pkg_zip(
    name = "external_generated.srcjar",
    srcs = ["java/com/example/external/gensrcjar/ExternalJavaSourceInGenSrcjar.java"],
    out = "external_gen.srcjar",
    strip_prefix = strip_prefix.from_root(),
)

java_library(
    name = "external_gensrcjar",
    srcs = [":external_generated.srcjar"],
    visibility = ["//tools/adt/idea/aswb/aswb/testdata/projects/deps:__subpackages__"],
)
