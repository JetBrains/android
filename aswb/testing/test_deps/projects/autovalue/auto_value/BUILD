load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

licenses(["notice"])

java_plugin(
    name = "autovalue-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = ["@rules_android_maven//:com_google_auto_value_auto_value"],
)

java_plugin(
    name = "autobuilder-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoBuilderProcessor",
    deps = ["@rules_android_maven//:com_google_auto_value_auto_value"],
)

java_plugin(
    name = "auto_annotation_plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = ["@rules_android_maven//:com_google_auto_value_auto_value"],
)

java_plugin(
    name = "autooneof-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = ["@rules_android_maven//:com_google_auto_value_auto_value"],
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
        "@rules_android_maven//:com_google_auto_value_auto_value",
        "@rules_android_maven//:com_google_auto_value_auto_value_annotations",
    ],
)
