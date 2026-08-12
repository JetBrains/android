load("@rules_android//android:rules.bzl", "android_library")

android_library(
    name = "external_resource",
    assets = ["assets/sample_asset.txt"],
    assets_dir = "assets",
    manifest = "AndroidManifest.xml",
    resource_files = ["res/drawable/foo.xml"],
    visibility = ["//visibility:public"],
)
