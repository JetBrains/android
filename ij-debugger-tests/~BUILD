load(":ij-debugger-tests.bzl", "debugger_test")

debugger_test(
    name = "k2-tests-jvm",
    expected_results = "k2-expected-results-jvm.txt",
    shard_count = 50,
    tags = ["manual"],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

debugger_test(
    name = "k2-tests-art",
    expected_results = "k2-expected-results-art.txt",
    run_on_art = True,
    shard_count = 50,
    tags = ["manual"],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)
