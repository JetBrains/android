"""This module implements IntelliJ Debugger Test rules."""

load("//tools/adt/idea/jps-build:idea.bzl", "jps_test")

def _debugger_test(
        name,
        suite,
        env = {},
        data = [],
        **kwargs):
    """Define a debugger test that runs on a ART.

    Args:
        name: the target name
        suite: the fqn of the test suite class
        env: a dict containing environment variables to set
        data: a list of targets to add to the test `runfiles` directory
        **kwargs: arguments to pass through to jps_test

    """

    jps_test(
        name = name,
        size = "large",
        download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_tests",
        module = "kotlin.jvm-debugger.test",
        test_suite = suite,
        env = env,
        data = data,
        deps = [
            ":test_lib",
            "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
            "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_tests",
            "//tools/idea:idea_source",
        ],
        **kwargs
    )

def jvm_debugger_test(
        name,
        suite,
        **kwargs):
    """Define a debugger test that runs on a JVM.

    Args:
        name: the target name
        suite: the fqn of the test suite class
        **kwargs: arguments to pass through to _debugger_test

    """

    _debugger_test(
        name = name,
        suite = suite,
        **kwargs
    )

def art_debugger_test(
        name,
        suite,
        **kwargs):
    """Define a debugger test that runs on a ART.

    Args:
        name: the target name
        suite: the fqn of the test suite class
        **kwargs: arguments to pass through to _debugger_test

    """

    _debugger_test(
        name = name,
        suite = suite,
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
