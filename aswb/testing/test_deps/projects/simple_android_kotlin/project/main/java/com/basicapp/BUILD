load("@rules_kotlin//kotlin:android.bzl", "kt_android_library")
load("@rules_kotlin//kotlin:core.bzl", "kt_kotlinc_options")

kt_kotlinc_options(
    name = "test_options",
    x_no_optimize = True,
)

genrule(
    name = "gen_res",
    outs = ["res/values/generated.xml"],
    cmd = "echo '<resources><string name=\"generated_string\">Generated</string></resources>' > $@",
)

kt_android_library(
    name = "basic_lib",
    srcs = ["BasicActivity.kt"],
    custom_package = "com.basicapp",
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]) + [":gen_res"],
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

kt_android_library(
    name = "basic_lib_custom",
    srcs = ["Custom.kt"],
    custom_package = "com.basicapp",
    kotlinc_opts = ":test_options",
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]) + [":gen_res"],
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)
