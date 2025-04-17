load("@rules_android//android:rules.bzl", "android_binary")
load("@rules_kotlin//kotlin:core.bzl", "kt_compiler_plugin")

android_binary(
    name = "simple_compose",
    manifest = "AndroidManifest.xml",
    visibility = ["//visibility:private"],
    deps = [
        "//simple_compose/main/java/com/basicapp:basic_lib",
    ],
)

kt_compiler_plugin(
    name = "jetpack_compose_compiler_plugin",
    id = "androidx.compose.compiler",
    target_embedded_compiler = True,
    visibility = ["//visibility:public"],
    deps = [
        "@rules_compose_maven//:org_jetbrains_kotlin_kotlin_compose_compiler_plugin_embeddable",
    ],
)
