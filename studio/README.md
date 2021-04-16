# Opening Android Studio in the IDE

* Open the project in `//tools/adt/idea`
* Set a [*Path Variable*](https://www.jetbrains.com/help/idea/settings-path-variables.html) named `SDK_PLATFORM` for the platform to use:
  * Linux: `linux/android-studio`
  * Mac: `darwin/android-studio/Contents`
  * Windows: `windows/android-studio`

*** note
Please note that in order for this Path Variable to take full effect, you need to close and reopen the project
***
If you are using IntelliJ 2020.2 or earlier, create a template JUnit configuration as described in
  [*Running tests using IntelliJ*](http://goto.corp.google.com/adtsetup#heading=h.31alixxsfo00)

# Updating the platform prebuilts

## From `go/ab`

The official way of updating prebuilts that can be commited to `prebuilts/studio/intellij-sdk` is to get them from the `go/ab` target [here](https://android-build.googleplex.com/builds/branches/git_studio-sdk-master-dev/grid?).

First identify the `<bid>` you want to update to. If you want to know what is the current build checked in into prebuilts you can look at the [METADATA](https://googleplex-android.git.corp.google.com/platform/tools/vendor/google_prebuilts/studio/intellij-sdk/+/refs/heads/studio-master-dev/AI-202/METADATA) file.

Then you can run the following command:

```
./tools/adt/idea/studio/update_sdk.py --download <bid>
```

which will update `tools/adt/idea` and `prebuilts/studio/intellij-sdk` with the new prebuilts. Note that if there jars are the same and there are no major version changes, `tools/adt/idea` won't need to be updated. At this point you are ready to upload the changes for review.

## From a locally built platform

In order to locally try changes to the platform, the prebuilts can be rebuilt as follows:

```
cd $SRC/tools/idea
./build_studio.sh
```

which will generate a set of artifacts in `tools/idea/out/studio/dist`. To update the prebuilts with these, run:

```
$SRC/tools/adt/idea/studio/update_sdk.py --path $SRC/tools/idea/out/studio/dist
```

Note: `build_studio.sh` does a clean build by default. You can do an incremental
build instead by specifying `-Dintellij.build.incremental.compilation=true`
in the `BUILD_PROPERTIES` array within `build_studio.sh`.

### Isolated builds

Note that in `go/ab` the prebuilts are built on a separate checkout to ensure that the `ant` build only has access to a few git repos.
If you want to have an isolated check out to build prebuilts you can checkout a separate repo like this:


```
repo init -u sso://googleplex-android.git.corp.google.com/platform/manifest -b studio-master-dev -m studio-sdk.xml
repo sync -j10
```

And execute `update_sdk.py` from the main repo with the path pointing to this isolated checkout.

# Building Android Studio

To build Android Studio run
```
bazel build //tools/adt/idea/studio:android-studio
```
and it will produce the following files:

```
bazel-bin/tools/adt/idea/studio/android-studio.linux.zip
bazel-bin/tools/adt/idea/studio/android-studio.mac.zip
bazel-bin/tools/adt/idea/studio/android-studio.win.zip
```

# Running Android Studio

Unzipping the files produced in the last step, should be all that is needed. But there is a handy utility on linux to do this:

```
bazel run //tools/adt/idea/studio:android-studio
```

will unzip the linux binary in a directory in `/tmp`, and will run it from there. The even handier: 

```
bazel run //tools/adt/idea/studio:android-studio -- --debug
```

will set it up to wait for a remote debugger connection on the `:5005` port.

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
