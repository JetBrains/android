load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

java_library_for_test_data(
    name = "inner",
    srcs = ["TestClassAnotherExternalDep.java"],
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = ["@maven//:com.google.code.gson.gson"],
)
