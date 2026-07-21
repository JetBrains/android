load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_test")

package(
    default_visibility = ["//visibility:public"],
)

kt_jvm_test(
    name = "SampleTest",
    srcs = ["java/com/example/tests/SampleTest.kt"],
    test_class = "com.example.tests.SampleTest",
    deps = [
        "@rules_android_maven//:junit_junit",
    ],
)
