package(default_visibility = ["//visibility:private"])

alias(
    name = "alias",
    actual = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps:nodeps",
)

java_library(
    name = "lib",
    srcs = ["Lib.java"],
    deps = [":alias"],
)
