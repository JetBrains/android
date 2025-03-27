load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "CcWorkspaceBuilderTest",
    srcs = ["CcWorkspaceBuilderTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc:cc_compilation_info_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/artifacts:aspect_protos",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:build_graphs",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "ConfigureCcCompilationTest",
    size = "small",
    srcs = ["ConfigureCcCompilationTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc:cc_compilation_info_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/artifacts:aspect_protos",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)
