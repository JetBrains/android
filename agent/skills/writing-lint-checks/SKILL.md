---
name: writing-lint-checks
description: Helps to write a new lint check under `/tools/base/lint/libs/lint-checks`.
---

## Writing a new lint check

1. Start from a description of the lint check. Ask the user for this, if not provided. This will also be the KDoc comment for the Detector class. It should usually have a form similar to:

```
Reports [calls to _ | references to _ | [the] _ element[s] | etc.] in [the merged manifest | manifest files | XML layout files | Kotlin/Java sources | etc.] if the following conditions hold:

- [condition 1]
- [condition 2]
- [etc.]

[Extra information, often including _why_ we want to report these things.]
```

Not all parts are required, but "Reports _" is important. Ask the user to try again, if necessary. A complex check might have multiple "Reports _" clauses.

2. Read one or more of the examples linked below to use as a reference, focusing on those that likely have a similar shape to the new check.

3. Create the new Detector (a Kotlin class) alongside the existing Detectors (or add to an existing Detector, if asked by the user).
  * Add the description as the KDoc for the class.
  * Prefer using early returns or `continue` when checking the conditions for reporting vs. nested conditionals, to avoid deep nesting.
  * Prefer "giving up" (often via an early return or `continue`) and reporting nothing, rather than reporting a "maybe" warning. The same approach usually works for null checks, such as for calls/references that don't resolve.

4. Add the new Issue(s) to `builtinIssues` in [BuiltinIssueRegistry](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/BuiltinIssueRegistry.kt), keeping the lines of the list definition sorted alphabetically.

5. Create the new test class (a Kotlin class) alongside the existing Detector tests (or add to an existing test class, if it already exists). Initially, just create a documentation test (usually, `fun testDocumentationExample()`). Run the test, and iterate a few times (updating the test and/or Detector) to make the test pass.

6. Tell the user that the lint check is not yet ready to submit, but is ready for checking and iteration. Tell the user that they can request to "finalize" the lint check; to do this, read [final-steps.md](final-steps.md).

### Examples

* [AccessibilityForceFocusDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/AccessibilityForceFocusDetector.kt)
  * Visits source code. Reports calls to a specific method, but only if certain conditions are met regarding the argument.

* [CredentialManagerMisuseDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/CredentialManagerMisuseDetector.kt)
  * Visits source code. Reports calls to specific methods, unless we see a reference to a certain exception (in any module).
  * Uses partial analysis to ensure we visit code from all dependencies to see if they reference the exception. Only reports incidents at the app module to ensure we have visited all (source) dependencies that are part of the final app.

* [UncaughtExceptionHandlerDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/UncaughtExceptionHandlerDetector.kt)
  * Just visits source code. Reports calls to a method, unless we see a call to another method _in the same module_.
  * Similar to `CredentialManagerMisuseDetector`, but we chose to only consider the current module, and so it does not use partial analysis. This choice may need to be clarified with the user.

* [SelectedPhotoAccessDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/SelectedPhotoAccessDetector.kt)
  * Just looks at the app's merged manifest. Reports certain elements depending on the attributes, and whether other elements exist in the merged manifest.

* [ManifestAttributeDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/ManifestAttributeDetector.kt)
  * Visits manifest elements. Reports manifest attributes. It only needs to look at each app and library module's manifest in isolation, rather than getting the _merged_ manifest.

* [PictureInPictureDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/PictureInPictureDetector.kt)
  * Visits source code, as well as looking at the app's merged manifest. Reports an element in the app's merged manifest depending on what was seen in the source code.
  * Uses partial analysis.

* [more-examples.md](more-examples.md) might be useful for more complex Kotlin/Java analysis: visiting method bodies, use of `UElementVisitor`, `EscapeCheckingDataFlowAnalyzer`, etc.

* More examples can be found by just searching for specific things in `/tools/base/lint/libs/lint-checks/src/test/java/com/android/tools/lint/checks/`.

### Partial analysis

For more details on partial analysis, read [CommuncationDeviceDetector](/tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/CommunicationDeviceDetector.kt).

