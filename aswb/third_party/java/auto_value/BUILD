licenses(["notice"])

java_plugin(
    name = "autovalue-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = ["@maven//:com.google.auto.value.auto-value"],
)

java_plugin(
    name = "autobuilder-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoBuilderProcessor",
    deps = ["@maven//:com.google.auto.value.auto-value"],
)

java_plugin(
    name = "auto_annotation_plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = ["@maven//:com.google.auto.value.auto-value"],
)

java_plugin(
    name = "autooneof-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = ["@maven//:com.google.auto.value.auto-value"],
)

# provides both the jar for compilation and the java_plugin.
java_library(
    name = "auto_value",
    exported_plugins = [
        ":autovalue-plugin",
        ":autobuilder-plugin",
        ":autooneof-plugin",
        ":auto_annotation_plugin",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@maven//:com.google.auto.value.auto-value",
        "@maven//:com.google.auto.value.auto-value-annotations",
    ],
)
