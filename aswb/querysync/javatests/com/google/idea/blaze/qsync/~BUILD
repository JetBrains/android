load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/adt/idea/aswb/testing:test_defs.bzl", "aswb_test")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

aswb_library(
    name = "TestUtils",
    testonly = 1,
    srcs = [
        "GraphToProjectConvertersForTests.kt",
        "QuerySyncTestUtils.java",
        "TestDataSyncRunner.kt",
    ],
    visibility = [
        ":__subpackages__",
        "//tools/adt/idea/aswb/base:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project/update",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)

java_test(
    name = "QuerySyncTestUtilsTest",
    srcs = ["QuerySyncTestUtilsTest.java"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)

aswb_test(
    name = "GraphToProjectConverterTest",
    srcs = ["GraphToProjectConverterTest.kt"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project/update",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:build_graphs",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)

java_test(
    name = "AffectedPackagesTest",
    size = "small",
    srcs = ["AffectedPackagesTest.java"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)

aswb_test(
    name = "ProjectRefresherTest",
    size = "small",
    srcs = ["ProjectRefresherTest.kt"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)

aswb_test(
    name = "PartialProjectRefreshTest",
    size = "small",
    srcs = ["PartialProjectRefreshTest.kt"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)

aswb_test(
    name = "JavaPackagePrefixReaderTest",
    srcs = ["JavaPackagePrefixReaderTest.kt"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata:build_graphs",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)

aswb_test(
    name = "FileProcessorTest",
    srcs = ["FileProcessorTest.kt"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
    ],
)

aswb_test(
    name = "DirectoryProcessorImplTest",
    srcs = ["DirectoryProcessorImplTest.kt"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/traverser",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

aswb_test(
    name = "ProjectStructureReaderTest",
    srcs = ["ProjectStructureReaderTest.kt"],
    deps = [
        ":TestUtils",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)
