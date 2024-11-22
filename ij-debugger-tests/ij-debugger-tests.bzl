"""This module implements IntelliJ Debugger Test rules."""

load("//tools/adt/idea/jps-build:idea.bzl", "jps_test")

def debugger_test(
        name,
        test_include_filter,
        test_exclude_filter = [],
        expected_to_fail_art = None,
        expected_to_fail_jvm = None,
        art_tags = [],
        jvm_tags = [],
        shard_count = None):
    """Define a debugger test that runs on a ART and JVM.

    Args:
        name: The base name of the tests
        test_include_filter: Patterns of tests to include
        test_exclude_filter: Patterns of tests to exclude
        expected_to_fail_art: A file with a list of tests that are expected to fail on ART
        expected_to_fail_jvm: A file with a list of tests that are expected to fail on JVM
        art_tags: Tags for the ART test
        jvm_tags: Tags for the JVM test
        shard_count: Number of shards to run
    """
    expected_to_fail_dep_art = []
    if expected_to_fail_art:
        expected_to_fail_dep_art = [":%s" % expected_to_fail_art]
    jps_test(
        name = "%s-art" % name,
        size = "large",
        shard_count = shard_count,
        test_include_filter = test_include_filter,
        test_exclude_filter = test_exclude_filter,
        expected_failures_file = expected_to_fail_art,
        data = [
            "//prebuilts/r8:r8-jar",
            "//prebuilts/tools/linux-x86_64/art",
            "//prebuilts/tools/linux-x86_64/art:art_deps",
        ],
        download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_tests",
        env = {
            "INTELLIJ_DEBUGGER_TESTS_VM_ATTACHER": "com.google.android.tools.debugger.test.ArtAttacher",
            "INTELLIJ_DEBUGGER_TESTS_DEX_CACHE": "$PWD/dex_cache",
            "INTELLIJ_DEBUGGER_TESTS_STUDIO_ROOT": "$PWD",
            "INTELLIJ_DEBUGGER_TESTS_TIMEOUT_MILLIS": "60000",
        },
        module = "kotlin.jvm-debugger.test",
        tags = art_tags,
        test_suite = "com.android.tools.test.ModuleTestSuite",
        runtime_deps = [":attacher"],
        deps = [
            ":kotlin.jvm-debugger.test_lib",
            ":test_repo.zip",
            "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
            "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_tests",
            "//tools/idea:idea_source",
        ] + expected_to_fail_dep_art,
    )

    expected_to_fail_dep_jvm = []
    if expected_to_fail_jvm:
        expected_to_fail_dep_jvm = [":%s" % expected_to_fail_jvm]
    jps_test(
        name = "%s-jvm" % name,
        size = "large",
        shard_count = shard_count,
        test_include_filter = test_include_filter,
        test_exclude_filter = test_exclude_filter,
        expected_failures_file = expected_to_fail_jvm,
        download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_tests",
        env = {
            "INTELLIJ_DEBUGGER_TESTS_VM_ATTACHER": "jvm",
        },
        module = "kotlin.jvm-debugger.test",
        tags = jvm_tags,
        test_suite = "com.android.tools.test.ModuleTestSuite",
        deps = [
            ":kotlin.jvm-debugger.test_lib",
            ":test_repo.zip",
            "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
            "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_tests",
            "//tools/idea:idea_source",
        ] + expected_to_fail_dep_jvm,
    )

    native.test_suite(
        name = name,
        tests = [
            "%s-art" % name,
            "%s-jvm" % name,
        ],
    )
