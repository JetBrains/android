package(default_visibility = ["//tools/adt/idea/streaming/testSrc/testApp/src/main:__subpackages__"])

android_library(
    name = "main_activity",
    srcs = ["MainActivity.java"],
    javacopts = [
        "-source",
        "8",
        "-target",
        "8",
    ],
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    tags = ["no_windows"],
)
