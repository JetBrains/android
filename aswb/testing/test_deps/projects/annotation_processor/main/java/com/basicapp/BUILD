load("@rules_android//android:rules.bzl", "android_library")
load("@rules_java//java:defs.bzl", "java_plugin")

android_library(
    name = "basic_lib",
    srcs = ["BasicActivity.java"],
    custom_package = "com.basicapp",
    manifest = "AndroidManifest.xml",
    plugins = [":hilt_android_entry_point_processor"],
    visibility = ["//visibility:public"],
    deps = [
        "@rules_dagger//:androidx_annotation_annotation_jvm",
        "@rules_dagger//:androidx_appcompat_appcompat",
        "@rules_dagger//:com_google_dagger_hilt_android",
    ],
)

java_plugin(
    name = "hilt_android_entry_point_processor",
    generates_api = 1,
    processor_class = "dagger.hilt.android.processor.internal.androidentrypoint.AndroidEntryPointProcessor",
    deps = ["@rules_dagger//:com_google_dagger_hilt_android_compiler"],
)
