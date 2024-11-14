load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "shared",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/exception",
    ],
)

java_library(
    name = "exception",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/exception",
    ],
)

java_library(
    name = "vcs",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/vcs",
    ],
)

java_library(
    name = "artifact",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/artifact",
    ],
)

java_library(
    name = "proto",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/proto",
    ],
)

test_suite(
    name = "tests",
    tests = [
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:LabelTest",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:TargetTreeTest",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/artifact:BuildArtifactCacheTest",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/proto:ProtoStringInternerTest",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/vcs:VcsStateTest",
    ],
    visibility = DEFAULT_TEST_VISIBILITY,
)
