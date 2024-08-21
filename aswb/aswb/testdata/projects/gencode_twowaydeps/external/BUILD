load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "external_sources_package",
)

package(
    default_visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects/gencode_twowaydeps:__subpackages__",
    ],
)

java_library(
    name = "annotation",
    srcs = ["java/com/example/external/annotation/Annotation.java"],
    exported_plugins = [":processor"],
    deps = [],
)

java_library(
    name = "interface",
    srcs = ["java/com/example/external/Interface.java"],
)

java_plugin(
    name = "processor",
    srcs = ["java/com/example/external/processor/Processor.java"],
    generates_api = 1,
    processor_class = "com.example.external.processor.Processor",
    deps = ["@maven//:com.google.guava.guava"],
)

external_sources_package(
    name = "sources",
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects/gencode_twowaydeps:__subpackages__",
    ],
)
