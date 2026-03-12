## Final steps

1. Check again that the Issue(s) have been added to `builtinIssues` in [BuiltinIssueRegistry](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/BuiltinIssueRegistry.kt), keeping the lines of the list definition sorted alphabetically.

2. Add additional tests to the test class, if useful.
  * If the lint check visits Kotlin/Java code:
    * For the documentation example test, prefer just using inline Kotlin test file(s).
    * Consider adding at least one additional test with inline Java test file(s) to ensure the UAST code works for Java as well.
  * Consider adding one test for each condition that must hold, where only that condition is unsatsified, such that no incidents are reported.
  * In the inline test files (provided as String literals), use package names (for both app id and Java/Kotlin package names) like "com.example.app", rather than something like "test.pkg", otherwise the inline test files can look like tests, rather than source files from an app.
  * Run the tests to ensure they pass, and update the lint check and/or tests until they pass, or give up and stop here (and inform the user).

3. Run the following test to ensure the issues are registered in the Android Studio IDE, replacing `$SRC` with the source root directory that contains the `WORKSPACE` file: `tools/base/bazel/bazel test //tools/adt/idea/android-lint:intellij.android.lint.tests_tests__other --test_filter=com.android.tools.idea.lint.LintInspectionRegistrationTest.testAllLintChecksRegistered --test_env=LINT_UPDATE_IN_PLACE_ROOT=$SRC --strategy=TestRunner=standalone`

