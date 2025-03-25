package(
    default_visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects/android1:__subpackages__",
    ],
)

android_library(
    name = "lib",
    srcs = glob(["lib/java/**/*.java"]),
    javacopts = ["-source 17 -target 17"],
    manifest = "lib/AndroidManifest.xml",
    resource_files = glob(["lib/res/**"]),
)
