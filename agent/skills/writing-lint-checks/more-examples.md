## More examples

* [AccessibilityViewScrollActionsDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/AccessibilityViewScrollActionsDetector.kt)
  * Reports overrides.
  * Visits method bodies to track certain elements and check if certain elements don't escape via `EscapeCheckingDataFlowAnalyzer`.
* [RestrictedEnvironmentBlockedCallDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/RestrictedEnvironmentBlockedCallDetector.kt)
  * Visits all call expressions to report calls to a large set of hardcoded methods, plus annotated methods.
  * Also records calls to a "check" method that indicates we should not report any incidents within the current method body because the potentially blocked calls are most likely guarded by the "check" call.

