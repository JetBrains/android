load("//tools/adt/idea/aswb:android.bzl", "android_library")

android_library(
    name = "android",
    srcs = ["TestAndroidClass.java"],
    javacopts = [
        "-source",
        "8",
    ],
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
)
