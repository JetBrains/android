load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

package(default_visibility = ["//tools/adt/idea/aswb/querysync:__subpackages__"])

aswb_library(
    name = "update",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    deps = [
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
    ],
)
