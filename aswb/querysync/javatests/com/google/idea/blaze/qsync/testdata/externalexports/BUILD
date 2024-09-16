load(":collect_deps.bzl", "java_info_txt")

java_library(
    name = "externalexports",
    srcs = ["TestClassUsingExternalExport.java"],
    deps = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/exports"],
)

java_info_txt(
    name = "java_info",
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = [":externalexports"],
)
