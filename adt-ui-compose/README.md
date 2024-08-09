# The `adt-ui-compose` Module

## Purpose

The `adt-ui-compose` serves as the Compose-based counterpart for the Swing-based `adt-ui` module. Similar to `adt-ui`, this module has two
main purposes: (1) Serve as a space for shareable UI components and utilities (2) To bring in all necessary dependencies for UI development.
However, contrary to adt-ui, this module is not supposed to depend on other modules. Its main role is to be a dependency for other modules.

## Structure

The module is split into two top-level directories: `src` and `testSrc`.

### Development Directory (`src`)

The `src` directory contains composables and utilities intended for sharing across Studio projects. It also includes sample composables,
some of which are used in the `testSrc` directory to demonstrate testing.

#### Notable Utilities

* `StudioComposePanel`: This function takes a composable and wraps + returns it within a JComponent using Studio theming. This simplifies
embedding composables into a Studio tool window, dialog, etc.

### Test Directory (`testSrc`)

The `testSrc/sample` directory contains a `SampleComposeWindow` that demonstrates how to create a standalone window application using
Compose Desktop and Jewel's standalone theme. The window showcases various components (defined in the
`src/sample/samplecomposewindow/components/`). This sample is in `testSrc` because the underlying theme utilized is the Jewel
standalone theme, a test-only dependency. This theme enables testing composables without the need to bring in the Jewel bridge dependencies.

#### Notable Utilities

* `StudioTestTheme`: This function simplifies UI testing by wrapping composables with the standalone theme, abstracting away the setup
process.
* `StudioTheme`: The theme all Studio developers should use in the IDE production code.

## How to Upgrade Jewel + Compose Desktop

1. Update the `/tools/base/bazel/maven/artifacts.bzl` file's DATA artifact entries to the desired version. While there's no single set of
artifacts to target, follow these general steps:
    1. Update the versions of artifacts prefixed with `org.jetbrains.jewel`.
    2. If Jewel's changelog mentions a Compose version upgrade, update the artifacts prefixed with `org.jetbrains.compose`.
    3. If Compose was upgraded, check the pre-upgrade and new `org.jetbrains.compose.foundation` artifacts' `POM` files for the old and new
       required Skiko versions. Upgrade *only* the Skiko artifacts used by the old Compose version. All Skiko artifacts should be prefixed
       with `org.jetbrains.skiko`.
    4. If Jewel's changelog mentions a Commonmark upgrade, update artifacts prefixed with `org.commonmark`.

    **NOTE: There is always the chance that the above steps are not sufficient, you must do your due diligence to find what is needed to be
    upgraded and added. Thankfully, when building and running Studio in step 6, you will get a good idea of what is missing.**

2. Fetch new/updated Maven artifacts and remove old/unused ones by following these [instructions](https://googleplex-android.googlesource.com/platform/tools/base/+/refs/heads/studio-main/bazel/README.md#fetching-new-maven-dependencies).

3. For all artifacts upgraded and added in step 1, reflect the respective upgrades and additions in the `adt-ui-compose` module's `iml`
file. If adding a new artifact, think carefully about which module-library you add it to. Ask yourself if it is a test-only artifact or not,
and whether it is a Jewel or Compose specific artifact. The answers to those questions should make the destination clear.

4. Update any other build file that relies on the upgraded Jewel. You can find uses of the old Jewel version by simply searching the
codebase for the version code. Please do **not** do a find and replace; it is better to inspect each match before upgrading the version.
One example can of this can be found in the `/platform/tools/vendor/google/ml/aiplugin/core/build.grade.kts` file.

5. Update the Studio `BUILD` files by following these [instructions](https://g3doc.corp.google.com/company/teams/android-studio/howto/updating_studio_build_files.md?cl=head)

6. Build and run Studio. Verify that the Compose UI renders fine in one of the tool windows utilizing Compose (e.g. Profiler), and one of
the dialogs using Compose (e.g. Tools > Internal Actions > Android > Add Device). Also, after uploading these changes, be sure to run
presubmit to see if all Compose-based UI tests are still passing.

7. Run `/tools/base/bazel/bazel test //tools/adt/idea/studio:test_studio`. You should expect a failure; the failure will generate new
expected test files that include the updated versions of the artifacts. You can find these generated test files zipped in
`/studio-main/bazel-testlogs/tools/adt/idea/studio/test_studio/test.outputs/outputs.zip`. Extract the new expected files, and copy them over
to `studio-main/tools/adt/idea/studio/tests`, replacing the outdated files.

8. Commit the changes in the affected repositories. For the tracking bug, create a bug for Jewel upgrade. 
    * Pick some version for the title "X.Y.Z"
    * Explain in the bug why you need to upgrade Jewel, and mark other bugs that require the upgrade as blocked on this bug.
    * Within the bug, explain modified/new/removed artifacts in the new release. (optional)
    * If any manual testing or presubmit/test failures arise, use the bug to discuss such results.

### Troubleshooting
**Issue**: Fetching Maven artifacts is failing.\
**Tips**: If it fails, inspect the failure log as it will indicate what specific artifact(s) it failed to fetch. It is very possible an
artifact the log mentions has not been published to the maven repository, so in order to proceed, first make sure all necessary artifacts
are published. Another possibility is that the necessary repository itself is not defined in `artifacts.bzl`. In that case, add the missing
repository.

**Issue**: Studio will not build/run, or, if it does, Compose UI is not rendering when invoking the Studio components.\
**Tips**: If there is any failure (whether at compile or runtime time), inspect the logs to determine the cause. Many times a class
loading error at runtime will be logged, pointing to the exact class needed that was not present. Find the  artifact that was supposed to
bring in said class, and add it to the `iml` file if it was already brought into the prebuilts (then repeat steps 3-5). If it was not
already present in the prebuilts, add it to `artifacts.bzl` (then repeat steps 2-5).