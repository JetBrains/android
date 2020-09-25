*** note
**Warning:** work in progress
***

# Opening Android Studio in the IDE

* Open the project in `//tools/adt/idea`
* Set a [*Path Variable*](https://www.jetbrains.com/help/idea/settings-path-variables.html) named `SDK_PLATFORM` for the platform to use:
  * Linux: `linux/android-studio`
  * Mac: `darwin/android-studio/Contents`
  * Windows: `windows/android-studio`

*** note
Please note that in order for this Path Variable to take full effect, you need to close and reopen the project
***

# Updating the platform prebuilts

## From `go/ab`

The ideal and official way of updating prebuilts that can be uploaded to `prebuilts/studio/intellij-sdk` is to do:

```
./tools/adt/idea/studio/update_sdk.py --download <bid>
```
Where `<bid>` is the build ID of the studio sdk target [here](https://android-build.googleplex.com/builds/branches/git_studio-sdk-master-dev/grid?).

This command will update your prebuilts and your library files in tools/adt/idea to point to the newly downloaded
platform.

## From a locally built platform

While the migration is in progress, the flow to modify the prebuilts for `tools/idea` is complicated, and will be improved overtime.
Because the tools/idea project still has references to android modules, we need a separate checkout with a few things. For example, let's
say your main checkout is at `$SRC/studio-master-dev`, and we create a new `$SRC/studio-sdk`:

```
cd $SRC/studio-sdk
repo init -u sso://googleplex-android.git.corp.google.com/platform/manifest -b studio-master-dev -m studio-sdk
repo sync -j10
```

Then we build the platform on this new checkout:

```
cd $SRC/studio-sdk/tools/idea
./build_studio.sh --studio-sdk
```

And finally we can import this prebuilts into the main checkout:

```
cd $SRC/studio-master-dev
./tools/adt/idea/studio/update_sdk.py --path $SRC/studio-sdk/tools/idea/out/studio/dist
```

*** note
Once the tools/idea project does not have any android references, we will be able to all this from the same checkout
***

# Building Android Studio

To build Android Studio run
```
bazel build //tools/adt/idea/studio:android-studio
```

## Searchable Options

IntelliJ has a post-build process to generate an index for things that can be searched in the UI. They perform
this operation by running the IDE headless with a "traverseUI" argument. All these generated files
are stored in `searchable-options` and we ensure its consistency via tests.

The test `//tools/adt/idea/studio:searchable_options_test` ensures that the bundled xmls are up-to-date.
If this test fails, its `outputs.zip` file contains the new .xmls that need to be updated.

Alternatively, running
```
bazel run //tools/adt/idea/studio:update_searchable_options
```
Will build the studio bundle, and update the source files with the latest index.

## Optimized builds

In order to build all dependencies with c++ optimized builds, stripped binaries, full zip compression, please use
```
bazel build //tools/adt/idea/studio:android-studio --config=release
```
Or our remote configuration which also implies release
```
bazel build //tools/adt/idea/studio:android-studio --config=remote
```


# Note: This is still work in progress

We are not yet using this mechanism to build and release Studio, as this is still in progress.
Currently all the new targets needed are tagged as manual, and live in parallel with the main iml_module targets. To generate the iml_modules for this project use:

```
bazel run //tools/base/bazel:iml_to_build -- --project_path tools/adt/idea --strict
```

This will create and/or update the iml_module rules. To update the attributes that are manually added to iml_module rules use:

```
bazel run //tools/base/bazel:fix_unbundled_rules
```

Note that to switch bazel to use the unbundled rules, the file //tools/base/bazel/project.bzl needs to be changed.

This is done for now by tools/base/bazel/studio_linux.sh before it runs the tests.
