load("@rules_android//android:rules.bzl", "android_binary")

android_binary(
    name = "basic_app",
    custom_package = "com.basicapp",
    manifest = "AndroidManifest.xml",
    deps = ["//annotation_processor/main/java/com/basicapp:basic_lib"],
)
