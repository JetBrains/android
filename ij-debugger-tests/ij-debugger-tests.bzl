"""This module implements IntelliJ Debugger Test rules."""

load("//tools/adt/idea/jps-build:idea.bzl", "jps_test")

def debugger_test(
        name,
        suite,
        **kwargs):
    """Define a debugger test that runs on a ART and JVM.

    Args:
        name: the target name
        suite: the fqn of the test suite class
        **kwargs: arguments to pass through to _debugger_test

    """

    jps_test(
        name = name + "_jvm",
        test_suite = suite,
        **kwargs
    )

    jps_test(
        name = name + "_art",
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
