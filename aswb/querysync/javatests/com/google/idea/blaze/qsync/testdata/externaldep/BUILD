load(":collect_deps.bzl", "java_info_txt")

java_library(
    name = "externaldep",
    srcs = ["TestClassExternalDep.java"],
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps"],
)

java_info_txt(
    name = "java_info",
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = [":externaldep"],
)
