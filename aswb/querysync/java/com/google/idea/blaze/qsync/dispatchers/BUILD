load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

package(default_visibility = [
    "//tools/adt/idea/aswb/querysync:__subpackages__",
])

aswb_library(
    name = "dispatchers",
    srcs = glob([
        "*.kt",
    ]),
    deps = [
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/vendor/google/aswb/plugin_api",
    ],
)
