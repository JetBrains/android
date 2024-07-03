load("//tools/adt/idea/jps-build:idea.bzl", "jps_library")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load(":ignored-tests.bzl", "TEST_EXCLUDE_FILTER")
load(":ij-debugger-tests.bzl", "debugger_test")

jps_library(
    name = "kotlin.jvm-debugger.test_lib",
    download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_lib",
    module = "kotlin.jvm-debugger.test",
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//tools/base/bazel/ci/conditions:studio-linux": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    deps = [
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
        "//tools/idea:idea_source",
    ],
)

debugger_test(
    name = "kotlin.jvm-debugger.test_tests",
    size = "large",
    download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_tests",
    module = "kotlin.jvm-debugger.test",
    shard_count = 50,
    suite = "com.android.tools.test.ModuleTestSuite",
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//tools/base/bazel/ci/conditions:studio-linux": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    test_exclude_filter = TEST_EXCLUDE_FILTER,
    deps = [
        ":kotlin.jvm-debugger.test_lib",
        ":test_repo.zip",
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_tests",
        "//tools/idea:idea_source",
    ],
)

maven_repository(
    name = "test_repo",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:org.jetbrains.annotations-java5_24.0.0",
    ],
    zip_prefix = "maven/",
)
