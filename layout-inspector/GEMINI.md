# How to run tests in this module

## How to run all the tests
```bash
bazel test //tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests
```

## How to run a specific test
```bash
bazel test //tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests --test_filter=MyTest
```