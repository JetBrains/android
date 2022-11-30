def generate_test_targets_with_multiple_runs(num_tests, tests = {}, jvm_flags = [], **kwargs):
    for (name, filter) in tests.items():
        for i in range(0, num_tests):
            test_name = name if i == 0 else "%s_Run%d" % (name, i + 1)
            native.java_test(
                name = test_name,
                jvm_flags = jvm_flags + ["-Dtest_filter=\"(%s)\"" % filter],
                **kwargs
            )
