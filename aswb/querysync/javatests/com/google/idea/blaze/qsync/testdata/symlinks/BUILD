java_library(
    name = "test",
    srcs = glob(["**/*.java"]) + [
        ":chained_link",
        ":dangling_link",
        ":dirlink",
        ":filelink",
    ],
    target_compatible_with = ["@platforms//:incompatible"],  # Skip build as it contains broken symlinks
    deps = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/symlinks/testdir:testlib"],
)
