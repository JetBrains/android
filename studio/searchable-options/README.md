## Searchable Options

IntelliJ has a post-build process to generate an index for things that can be searched in the UI. They perform
this operation by running the IDE headless with a "traverseUI" argument. All these generated files
are stored in `searchable-options` and we ensure its consistency via tests.

The test `//tools/adt/idea/studio:<android-studio-name>.searchable_options_test` ensures that the bundled xmls are up-to-date.
If this test fails, its `outputs.zip` file contains the new .xmls that need to be updated.

Running
```
bazel run //tools/adt/idea/studio:<android-studio-name>.update_searchable_options
```
Will build the studio bundle, and update the source files with the latest index.

Alternatively, running
```
bazel run //tools/adt/idea/studio:update_searchable_options
```
Will update the searchable options for all the android studio flavors in the workspace.