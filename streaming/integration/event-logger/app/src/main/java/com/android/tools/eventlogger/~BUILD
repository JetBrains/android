package(default_visibility = ["//tools/adt/idea/streaming/integration/event-logger:__pkg__"])

android_library(
    name = "event_logging_activity",
    srcs = ["EventLoggingActivity.java"],
    javacopts = [
        "-source",
        "8",
        "-target",
        "8",
    ],
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
)
