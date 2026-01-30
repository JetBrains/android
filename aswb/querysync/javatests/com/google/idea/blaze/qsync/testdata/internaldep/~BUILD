load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

java_library_for_test_data(
    name = "internaldep",
    srcs = ["TestClassInternalDep.java"],
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps"],
)
