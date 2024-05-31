load("//tools/adt/idea/jps-build:idea.bzl", "jps_cache")

jps_cache(
    name = "cache",
    srcs = glob([
        "cache/**",
    ]),
    strip_prefix = "tools/adt/idea/jps-build/tests/cache/",
    visibility = ["//visibility:public"],
)

jps_cache(
    name = "test_cache",
    srcs = glob([
        "test_cache/**",
    ]),
    strip_prefix = "tools/adt/idea/jps-build/tests/test_cache/",
    visibility = ["//visibility:public"],
)
