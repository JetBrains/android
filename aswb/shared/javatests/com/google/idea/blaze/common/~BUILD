package(
    default_visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
        "//tools/adt/idea/aswb/querysync:__subpackages__",
        "//tools/adt/idea/aswb/shared:__subpackages__",
    ],
)

java_test(
    name = "LabelTest",
    size = "small",
    srcs = ["LabelTest.java"],
    deps = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
    ],
)

java_library(
    name = "test_utils",
    srcs = [
        "LoggingContext.java",
        "NoopContext.java",
    ],
    deps = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/vendor/google/aswb/third_party/java/flogger",
    ],
)

java_test(
    name = "TargetTreeTest",
    size = "small",
    srcs = ["TargetTreeTest.java"],
    deps = [
        "//tools/adt/idea/aswb/shared",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
    ],
)

java_test(
    name = "AtomicFileWriterTest",
    size = "small",
    srcs = ["AtomicFileWriterTest.java"],
    deps = [
        "//tools/adt/idea/aswb/shared",
        "//tools/vendor/google/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/third_party/java/truth",
    ],
)
