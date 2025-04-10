load("@rules_kotlin//kotlin:android.bzl", "kt_android_library")

package(
    default_visibility = ["//visibility:public"],
)

kt_android_library(
    name = "basic_lib",
    srcs = ["MainActivity.kt"],
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        "//simple_compose/main/java/com/basicapp/ui/theme",
        "@rules_compose_maven//:androidx_activity_activity_compose",
        "@rules_compose_maven//:androidx_compose_foundation_foundation_layout",
        "@rules_compose_maven//:androidx_compose_material3_material3",
        "@rules_compose_maven//:androidx_compose_runtime_runtime",
        "@rules_compose_maven//:androidx_compose_ui_ui_tooling",
        "@rules_compose_maven//:androidx_compose_ui_ui_tooling_preview",
    ],
)
