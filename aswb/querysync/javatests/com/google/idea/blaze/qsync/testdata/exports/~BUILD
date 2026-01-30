load("//tools/adt/idea/aswb/build_defs:test_data_build_defs.bzl", "java_library_for_test_data")

java_library_for_test_data(
    name = "exports",
    srcs = ["TestClassUsingExport.java"],
    visibility = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externalexports:__pkg__"],
    deps = [":exported-collect"],
)

java_library_for_test_data(
    name = "exported-collect",
    exports = ["//tools/vendor/google/aswb/plugin_api/maven:guava"],
)
