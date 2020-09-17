*** note
**Warning:** work in progress
***

# Opening Android Studio in the IDE

* Open the project in `//tools/adt/idea`
* Set a [*Path Variable*](https://www.jetbrains.com/help/idea/settings-path-variables.html) named `SDK_PLATFORM` for the platform to use:
  * Linux: `linux/android-studio`
  * Mac: `darwin/android-studio/Contents`
  * Windows: `windows/android-studio`

You are good to go.

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
If this test fails, its `outputs.zip` file contains the new .xmls that need to be udpdated.

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
Currently all the new targets needed are tagged as manual, and live in paralell with the main iml_module targets. To generate the iml_modules for this project use:

```
bazel run //tools/base/bazel:iml_to_build -- --project_path tools/adt/idea --strict
```

This will create and/or update the iml_module rules. To update the attributes that are manually added to iml_module rules use:

```
bazel run //tools/base/bazel:fix_unbundled_rules
```

Note that to switch bazel to use the unbundled rules, the file //tools/base/bazel/project.bzl needs to be changed.

This is done for now by tools/base/bazel/studio_linux.sh before it runs the tests.
