# Description: Indirections for SDK changes to the underlying platform library.

load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "SDK_COMPAT_VISIBILITY",
    "create_sdkcompat_visibility_package",
)

licenses(["notice"])

create_sdkcompat_visibility_package()

java_library(
    name = "sdkcompat",
    visibility = SDK_COMPAT_VISIBILITY,
    exports = ["//tools/adt/idea/aswb/sdkcompat/v233"],
)
