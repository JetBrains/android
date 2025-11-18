load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
])

aswb_library(
    name = "traverser",
    srcs = glob(["*.kt"]),
    deps = [
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/dispatchers",
        "//tools/vendor/google/aswb/plugin_api",
    ],
)
