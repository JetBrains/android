load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

java_library_for_test_data(
    name = "nested",
    srcs = ["TestClassExternalDep.java"],
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = ["//tools/vendor/google/aswb/plugin_api/maven:guava"],
)
