load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "ASWB_SUBPACKAGES_VISIBILITY",
    "TEST_ASWB_SUBPACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "combine_visibilities",
)

java_library(
    name = "tokens",
    srcs = glob(["*.java"]),
    visibility = combine_visibilities(
        ASWB_SUBPACKAGES_VISIBILITY,
        TEST_ASWB_SUBPACKAGES_VISIBILITY,
    ),
    deps = [
        "//tools/adt/idea/aswb/aswb:aswb_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/cpp",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/vendor/google/aswb/plugin_api",
    ],
)
