load("@rules_kotlin//kotlin:android.bzl", "kt_android_library")

package(
    default_visibility = ["//visibility:public"],
)

kt_android_library(
    name = "theme",
    srcs = [
        "Color.kt",
        "Theme.kt",
        "Type.kt",
    ],
    manifest = "AndroidManifest.xml",
    plugins = ["//simple_compose/main:jetpack_compose_compiler_plugin"],
    deps = [
        "@rules_compose_maven//:androidx_compose_material3_material3",
        "@rules_compose_maven//:androidx_compose_runtime_runtime",
    ],
)
