load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "PackageStatementParserTest",
    size = "small",
    srcs = ["PackageStatementParserTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "AddCompiledJavaDepsTest",
    size = "small",
    srcs = ["AddCompiledJavaDepsTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:project_protos",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "AddDependencyGenSrcsJarsTest",
    size = "small",
    srcs = ["AddDependencyGenSrcsJarsTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
        "@maven//:org.mockito.mockito-core",
    ],
)

java_test(
    name = "AddDependencySrcJarsTest",
    size = "small",
    srcs = ["AddDependencySrcJarsTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)

java_test(
    name = "AddProjectGenSrcJarsTest",
    size = "small",
    srcs = ["AddProjectGenSrcJarsTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:org.mockito.mockito-core",
    ],
)

java_test(
    name = "AddProjectGenSrcsTest",
    size = "small",
    srcs = ["AddProjectGenSrcsTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:org.mockito.mockito-core",
    ],
)

java_test(
    name = "AddDependencyAarsTest",
    size = "small",
    srcs = ["AddDependencyAarsTest.java"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:org.mockito.mockito-core",
    ],
)
