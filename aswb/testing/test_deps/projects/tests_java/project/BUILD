load("@rules_java//java:defs.bzl", "java_test")

package(
    default_visibility = ["//visibility:public"],
)

java_test(
    name = "SampleTest",
    srcs = [
        "java/com/example/tests/SampleTest.java",
        "java/com/example/tests/SampleTest2.java",
    ],
    test_class = "com.example.tests.SampleTest",
    deps = [
        "@rules_android_maven//:junit_junit",
    ],
)
