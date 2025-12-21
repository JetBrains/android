load("@rules_android//android:rules.bzl", "android_library")

genrule(
    name = "gen_res",
    outs = ["res/values/generated.xml"],
    cmd = "echo '<resources><string name=\"generated_string\">Generated</string></resources>' > $@",
)

android_library(
    name = "basic_lib",
    srcs = ["BasicActivity.java"],
    custom_package = "com.basicapp",
    javacopts = ["-source 11 -target 11"],
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]) + [":gen_res"],
    visibility = ["//visibility:public"],
)
