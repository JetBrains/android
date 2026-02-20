# Project Memory
Update this file with any surprising or unexpected project-specific details discovered during work. This serves as a long-term memory to avoid rediscovering the same issues in future sessions.

Keep this file up to date as information changes/goes out of date.

# Tests

## How to run all the tests
```bash
bazel test //tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests
```

## How to run a specific test
```bash
bazel test //tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests --test_filter=MyTest
```