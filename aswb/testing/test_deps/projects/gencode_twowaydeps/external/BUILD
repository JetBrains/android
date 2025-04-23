load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

package(
    default_visibility = ["//visibility:public"],
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
    deps = ["@maven//:com_google_guava_guava"],
)
