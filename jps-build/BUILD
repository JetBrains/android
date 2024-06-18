load("//tools/base/bazel:utils.bzl", "flat_archive")

py_binary(
    name = "jps_build",
    srcs = ["jps_build.py"],
    visibility = ["//visibility:public"],
)

py_test(
    name = "jps_build_test",
    srcs = [
        "jps_build.py",
        "jps_build_test.py",
    ],
    target_compatible_with = ["@platforms//os:linux"],
)

flat_archive(
    name = "test_runner",
    ext = "zip",
    visibility = ["//visibility:public"],
    deps = {
        "jps_test.sh": "tools/idea",
    },
)

java_binary(
    name = "jps-test-runner",
    srcs = glob(["jps-test-runner/**"]),
    main_class = "com.google.testing.junit.runner.BazelTestRunner",
    visibility = ["//visibility:public"],
    deps = ["@bazel_tools//tools/jdk:TestRunner"],
)
