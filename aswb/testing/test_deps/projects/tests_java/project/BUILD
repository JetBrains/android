load("@rules_java//java:defs.bzl", "java_library", "java_test")

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

java_test(
    name = "ConcreteTestCase",
    srcs = [
        "java/com/example/tests/AbstractTestCase.java",
        "java/com/example/tests/ConcreteTestCase.java",
        "java/com/example/tests/ConcreteTestCase2.java",
    ],
    test_class = "com.example.tests.ConcreteTestCase",
    deps = [
        "@rules_android_maven//:junit_junit",
    ],
)

java_library(
    name = "shared_test_lib",
    srcs = [
        "java/com/example/tests/SharedLibTest.java",
    ],
    deps = [
        "@rules_android_maven//:junit_junit",
    ],
)

java_test(
    name = "FirstSharedTest",
    test_class = "com.example.tests.SharedLibTest",
    runtime_deps = [
        ":shared_test_lib",
        "@rules_android_maven//:junit_junit",
    ],
)

java_test(
    name = "SecondSharedTest",
    test_class = "com.example.tests.SharedLibTest",
    runtime_deps = [
        ":shared_test_lib",
        "@rules_android_maven//:junit_junit",
    ],
)
