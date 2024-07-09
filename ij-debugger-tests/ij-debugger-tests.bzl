"""This module implements IntelliJ Debugger Test rules."""

load("//tools/adt/idea/jps-build:idea.bzl", "jps_test", "split")

def debugger_test(
        name,
        suite,
        split_tests_jvm,
        test_exclude_filter_jvm,
        split_tests_art,
        test_exclude_filter_art,
        **kwargs):
    """Define a debugger test that runs on a ART and JVM.

    Args:
        name: the target name
        suite: the fqn of the test suite class
        split_tests_jvm: A dictionary of test name suffix to test filter to include in the split.
                         The test filter can be constructed with the function 'shard' if shards are needed.
                         This is for the jvm based tests.
        split_tests_art: Similar but for the ART tests.
        test_exclude_filter_jvm: A list of exclude filters for the jvm tests.
        test_exclude_filter_art: A list of exclude filters for the ART tests.
        **kwargs: arguments to pass through to _debugger_test

    """

    jps_test(
        name = name + "_jvm",
        test_exclude_filter = test_exclude_filter_jvm,
        test_suite = suite,
        split_tests = _make_splits(split_tests_jvm),
        **kwargs
    )

    jps_test(
        name = name + "_art",
        test_exclude_filter = test_exclude_filter_art,
        split_tests = _make_splits(split_tests_art),
        test_suite = suite,
        env = {
            "INTELLIJ_DEBUGGER_TESTS_VM_ATTACHER": "art",
            "INTELLIJ_DEBUGGER_TESTS_STUDIO_ROOT": "$PWD",
        },
        data = [
            "//prebuilts/r8:r8-jar",
            "//prebuilts/tools/linux-x86_64/art:art",
            "//prebuilts/tools/linux-x86_64/art:art_deps",
        ],
        **kwargs
    )

def shard(filter, shard_count):
    return struct(filter = filter, shard_count = shard_count)

def _make_splits(splits):
    ret = []
    for k, v in splits.items():
        name = k
        filter = None
        shard_count = None
        if type(v) == "string":
            filter = v
        else:
            filter = v.filter
            shard_count = v.shard_count
        ret.append(
            split(
                name = name,
                filter = filter,
                shard_count = shard_count,
            ),
        )
    return ret
