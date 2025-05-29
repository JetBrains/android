# A blaze package containing a source file that's compiled for two targets.

java_library(
    name = "nodeps",
    srcs = ["TestClassMultiTarget.java"],
)

java_library(
    name = "externaldep",
    srcs = [
        "TestClassMultiTarget.java",
        "TestClassSingleTarget.java",
    ],
    deps = ["@maven//:com.google.guava.guava"],
)
