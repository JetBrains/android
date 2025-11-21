java_library(
    name = "exports",
    srcs = ["TestClassUsingExport.java"],
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externalexports:__pkg__"],
    deps = [":exported-collect"],
)

java_library(
    name = "exported-collect",
    exports = ["@maven//:com.google.guava.guava"],
)
