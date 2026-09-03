load("@rules_java//java:defs.bzl", "java_test")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library", "kt_jvm_test")

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

kt_jvm_test(
    name = "ConcreteKotlinTestCase",
    srcs = [
        "java/com/example/tests/AbstractKotlinTestCase.kt",
        "java/com/example/tests/AbstractParameterizedKotlinTestCase.kt",
        "java/com/example/tests/ConcreteJavaSubclass.java",
        "java/com/example/tests/ConcreteKotlinTestCase.kt",
        "java/com/example/tests/ConcreteParameterizedKotlinTestCase.kt",
    ],
    test_class = "com.example.tests.ConcreteKotlinTestCase",
    deps = [
        "@rules_android_maven//:junit_junit",
    ],
)

kt_jvm_test(
    name = "ConcreteKotlinSubclassOfJavaBase",
    srcs = [
        "java/com/example/tests/AbstractJavaBaseInKotlinProject.java",
        "java/com/example/tests/ConcreteKotlinSubclassOfJavaBase.kt",
    ],
    test_class = "com.example.tests.ConcreteKotlinSubclassOfJavaBase",
    deps = [
        "@rules_android_maven//:junit_junit",
    ],
)

kt_jvm_library(
    name = "test_lib_with_runtime_dep",
    srcs = ["java/com/example/tests/TestInLibraryWithRuntimeDep.kt"],
    deps = [
        "@rules_android_maven//:junit_junit",
    ],
)

java_test(
    name = "TestTargetWithRuntimeDep",
    test_class = "com.example.tests.TestInLibraryWithRuntimeDep",
    runtime_deps = [
        ":test_lib_with_runtime_dep",
    ],
)
