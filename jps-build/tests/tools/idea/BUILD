load("//tools/adt/idea/jps-build:idea.bzl", "idea_source", "jps_library", "jps_test")

idea_source(
    name = "source",
    include = ["**"],
    base_dir = "tools/adt/idea/jps-build/tests",
    exclude = ["BUILD"],
    target_compatible_with = ["@platforms//os:linux"],
    target_dir = "tools/idea",
    visibility = ["//visibility:public"],
)

jps_library(
    name = "jps_lib",
    cmd = "build.sh",
    download_cache = "tools/adt/idea/jps-build/tests/cache",
    module = "module.to.build",
    target_compatible_with = ["@platforms//os:linux"],
    deps = [
        ":source",
        ":test_zip",
        "//tools/adt/idea/jps-build/tests:cache",
    ],
)

jps_test(
    name = "jps_test",
    download_cache = "tools/adt/idea/jps-build/tests/test_cache",
    module = "module.to.test",
    target_compatible_with = ["@platforms//os:linux"],
    test_suite = "Test",
    deps = [
        ":jps_lib",
        ":source",
        "//tools/adt/idea/jps-build/tests:cache",
        "//tools/adt/idea/jps-build/tests:test_cache",
    ],
)

java_library(
    name = "test",
    srcs = ["Test.java"],
    deps = ["@bazel_tools//tools/jdk:TestRunner"],
)

genrule(
    name = "test_zip",
    srcs = [":libtest.jar"],
    outs = ["test.zip"],
    cmd = "cp $< $@",
)
