load(":art-k2-failing-tests.bzl", "ART_K2_FAILING")
load(":ij-debugger-tests.bzl", "debugger_test")

smoke_test = "include-methodname=org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2PositionManagerTestGenerated\\$SingleFile#testClass"

debugger_test(
    name = "jvm-smoke-test",
    filters = [smoke_test],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

debugger_test(
    name = "art-smoke-test",
    filters = [smoke_test],
    run_on_art = True,
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

test_suite(
    name = "smoke-test",
    tests = [
        ":art-smoke-test",
        ":jvm-smoke-test",
    ],
)

debugger_test(
    name = "jvm-k2-tests",
    expected_results = "jvm-k2-expected-results.txt",
    shard_count = 50,
    tags = [
        "ci:studio-linux_very_flaky",
        "noci:studio-linux",
    ],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

debugger_test(
    name = "art-k2-tests",
    expected_results = "art-k2-expected-results.txt",
    # Coroutines tests are too flaky and sometimes deadlock and fail the entire shard
    filters = [
        "exclude-classname=.*InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated.*\\$Coroutines\\$.*",
        "exclude-classname=.*K2IdeK2CodeKotlinEvaluateExpressionTestGenerated.*\\$Coroutines\\$.*",
    ],
    run_on_art = True,
    shard_count = 50,
    tags = [
        "ci:studio-linux_very_flaky",
        "noci:studio-linux",
    ],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

# A target of just the failing tests
debugger_test(
    name = "art-k2-failing-tests",
    filters = ART_K2_FAILING,
    run_on_art = True,
    shard_count = 50,
    tags = ["manual"],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)
