"""This module implements IntelliJ Debugger Test rules."""

load("@rules_java//java:defs.bzl", "java_test")
load("@tools_idea//build:tests-options.bzl", "ADD_OPENS_FLAGS", "JAVA_TEST_FLAGS", "TEST_FRAMEWORK_DEPS")
load("@tools_idea//plugins/kotlin:kotlin_test_dependencies.bzl", "all_test_dep_targets")

def include_tests_filter(tests):
    patterns = "|".join([test.replace("$", "\\$").replace(".", "\\.") for test in tests])
    return "include-methodname=" + patterns

def debugger_test(
        name,
        test_dep,
        expected_results = None,
        filter = None,
        run_on_art = False,
        **kwargs):
    """Define a debugger test that a JVM.

    Args:
        name: The base name of the tests
        test_dep: The jar dep that contains the tests
        expected_results: An option text file containing expected results
        filter: An option filter
        run_on_art: Specifies whether to run on ART or JVM
        **kwargs: Additional arguments for java_test
    """
    jvm_flags = JAVA_TEST_FLAGS + ADD_OPENS_FLAGS

    runtime_deps = [
        test_dep,
        "//tools/adt/idea/ij-debugger-tests/lib",
        "@tools_idea//:main_test_lib",
    ] + [it.replace("@community/", "@tools_idea/").replace("@lib/", "@tools_idea_lib/") for it in TEST_FRAMEWORK_DEPS]

    test_jar = test_dep.removeprefix("@tools_idea/").replace(":", "/") + ".jar"
    data = [
        "@debugger_test_deps_debugger_agent//file:debugger-agent.jar",
        "@tools_idea//plugins/kotlin/jvm-debugger/test:testData",
        "@tools_idea//plugins/kotlin/idea/tests:testData",
        "@tools_idea//java:mockJDK",
    ] + all_test_dep_targets

    env = {
        "JB_TEST_SANDBOX": "true",
        "JB_TEST_JAR": test_jar,
        "JB_TEST_EXCECUTION_RESULT_INTERCEPTOR": "com.google.android.tools.debugger.test.lib.ExpectedFailuresInterceptor",
    }

    if expected_results:
        env = env | {"EXPECTED_RESULTS_FILE": "$(location " + expected_results + ")"}
        data = data + [expected_results]

    if filter:
        env = env | {"JB_TEST_JUNIT5_FILTERS": filter}

    if run_on_art:
        env = env | {
            "INTELLIJ_DEBUGGER_TESTS_VM_ATTACHER": "com.google.android.tools.debugger.test.lib.ArtAttacher",
            "INTELLIJ_DEBUGGER_TESTS_DEX_CACHE": "./dex_cache",
            "INTELLIJ_DEBUGGER_TESTS_TIMEOUT_MILLIS": "60000",
            "INTELLIJ_DEBUGGER_TESTS_STUDIO_ROOT": ".",
        }
        data = data + [
            "//tools/adt/idea/.idea/libraries:r8",
            "//prebuilts/tools/linux-x86_64/art",
            "//prebuilts/tools/linux-x86_64/art:art_deps",
        ]

    java_test(
        name = name,
        data = data,
        env = env,
        jvm_flags = jvm_flags,
        main_class = "com.intellij.tests.JUnit5BazelRunner",
        runtime_deps = runtime_deps,
        target_compatible_with = select({
            "@platforms//os:windows": ["@platforms//:incompatible"],
            "@platforms//os:macos": ["@platforms//:incompatible"],
            "//conditions:default": ["@platforms//:incompatible"],
        }),
        timeout = "long",
        use_testrunner = False,
        **kwargs
    )
