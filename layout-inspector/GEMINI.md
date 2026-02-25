# Project Memory
Update this file with any surprising or unexpected project-specific details discovered during work. This serves as a long-term memory to avoid rediscovering the same issues in future sessions.

Keep this file up to date as information changes/goes out of date.

# General information
- **bazel location**: `studio-main/tools/base/bazel/bazel`
- **how to build the module**: `bazel build //tools/adt/idea/layout-inspector:intellij.android.layout-inspector`
- **how to run all tests**: `bazel test //tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests`
- **how to run a specific test**: `bazel test //tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests --test_filter=MyTest`