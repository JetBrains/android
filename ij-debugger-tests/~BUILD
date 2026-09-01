load(":ij-debugger-tests.bzl", "debugger_test")

smoke_test = "include-methodname=org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2PositionManagerTestGenerated\\$SingleFile#testClass"

debugger_test(
    name = "smoke-test-jvm",
    filters = [smoke_test],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

debugger_test(
    name = "smoke-test-art",
    filters = [smoke_test],
    run_on_art = True,
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

test_suite(
    name = "smoke-test",
    tests = [
        ":smoke-test-art",
        ":smoke-test-jvm",
    ],
)

debugger_test(
    name = "k2-tests-jvm",
    expected_results = "k2-expected-results-jvm.txt",
    shard_count = 50,
    tags = [
        "ci:studio-linux_very_flaky",
        "noci:studio-linux",
    ],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

debugger_test(
    name = "k2-tests-art",
    expected_results = "k2-expected-results-art.txt",
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
