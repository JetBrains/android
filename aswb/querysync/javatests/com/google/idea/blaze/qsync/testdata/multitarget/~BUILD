# A blaze package containing a source file that's compiled for two targets.

load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

java_library_for_test_data(
    name = "nodeps",
    srcs = ["TestClassMultiTarget.java"],
)

java_library_for_test_data(
    name = "externaldep",
    srcs = [
        "TestClassMultiTarget.java",
        "TestClassSingleTarget.java",
    ],
    deps = ["//tools/vendor/google/aswb/plugin_api/maven:guava"],
)
