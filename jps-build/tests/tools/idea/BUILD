load("//tools/adt/idea/jps-build:idea.bzl", "idea_source", "jps_library", "jps_test", "split")

idea_source(
    name = "source",
    include = ["**"],
    exclude = ["BUILD"],
    strip_prefix = "tools/adt/idea/jps-build/tests/",
    target_compatible_with = ["@platforms//os:linux"],
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
    split_tests = [
        split(
            name = "A",
            filter = "com.android.tools.test.Test1",
        ),
        split(
            name = "C",
            filter = "com.android.tools.test.Test3",
        ),
    ],
    target_compatible_with = ["@platforms//os:linux"],
    test_exclude_filter = [
        "com.android.tools.test.Test2",
        "com.android.tools.test.Test1#testFilterIndividualTest",
    ],
    test_suite = "com.android.tools.test.ModuleTestSuite",
    deps = [
        ":jps_lib",
        ":source",
        "//tools/adt/idea/jps-build/tests:cache",
        "//tools/adt/idea/jps-build/tests:test_cache",
    ],
)

java_library(
    name = "test",
    srcs = [
        "InnerRunner.java",
        "Test1.java",
        "Test2.java",
        "Test3.java",
    ],
    deps = ["@bazel_tools//tools/jdk:TestRunner"],
)

genrule(
    name = "test_zip",
    srcs = [":libtest.jar"],
    outs = ["test.zip"],
    cmd = "cp $< $@",
)
