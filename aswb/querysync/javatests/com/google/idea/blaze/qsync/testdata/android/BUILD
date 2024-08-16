load("//tools/vendor/google/aswb:android.bzl", "android_library")

android_library(
    name = "android",
    srcs = ["TestAndroidClass.java"],
    javacopts = [
        "-source",
        "8",
    ],
    manifest = "AndroidManifest.xml",
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
)
