load("@rules_java//java:defs.bzl", "java_library")
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
    module = "module.to.test",
    target_compatible_with = ["@platforms//os:linux"],
    deps = [
        ":source",
        ":test_zip",
        "//tools/adt/idea/jps-build/tests:cache",
    ],
)

filegroup(
    name = "expected-failures",
    srcs = [
        "others-expected-failures.txt",
        "test1-expected-failures.txt",
        "test3-expected-failures.txt",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "custom_dependency",
    srcs = ["Class.java"],
)

jps_test(
    name = "test1",
    download_cache = "tools/adt/idea/jps-build/tests/test_cache",
    expected_failures_file = ":test1-expected-failures.txt",
    module = "module.to.test",
    test_include_filter = ["com.android.tools.test.Test1"],
    test_suite = "com.android.tools.test.ModuleTestSuite",
    runtime_deps = [
        ":custom_dependency",
    ],
    deps = [
        ":jps_lib",
        ":source",
        ":test1-expected-failures.txt",
        "//tools/adt/idea/jps-build/tests:cache",
        "//tools/adt/idea/jps-build/tests:test_cache",
    ],
)

jps_test(
    name = "others",
    download_cache = "tools/adt/idea/jps-build/tests/test_cache",
    expected_failures_file = ":others-expected-failures.txt",
    module = "module.to.test",
    test_exclude_filter = [
        "com.android.tools.test.Test1",
        "com.android.tools.test.Test3",
    ],
    test_suite = "com.android.tools.test.ModuleTestSuite",
    deps = [
        ":jps_lib",
        ":others-expected-failures.txt",
        ":source",
        "//tools/adt/idea/jps-build/tests:cache",
        "//tools/adt/idea/jps-build/tests:test_cache",
    ],
)

jps_test(
    name = "test3",
    download_cache = "tools/adt/idea/jps-build/tests/test_cache",
    expected_failures_file = ":test3-expected-failures.txt",
    module = "module.to.test",
    test_include_filter = ["com.android.tools.test.Test3"],
    test_suite = "com.android.tools.test.ModuleTestSuite",
    deps = [
        ":jps_lib",
        ":source",
        ":test3-expected-failures.txt",
        "//tools/adt/idea/jps-build/tests:cache",
        "//tools/adt/idea/jps-build/tests:test_cache",
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
        "com.android.tools.test.Test3.Nested2#testNested10",
    ],
    test_suite = "com.android.tools.test.ModuleTestSuite",
    runtime_deps = [
        ":custom_dependency",
    ],
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
