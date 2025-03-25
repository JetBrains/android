Android Studio Plugin for [Bazel](https://bazel.build/) Projects
================================================================

An open-sourced Android Studio plugin for Bazel projects useful to import,
develop and run Bazel projects within Android Studio.

Detailed usage instructions are available [here](https://ij.bazel.build/).

### Plugin Releases

The Android Studio with Bazel (ASwB) Plugin is uploaded to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/9185-bazel-for-android-studio/versions/stable). Users can download it from there and manually install it or directly get it from the IDE by going to `Settings -> Plugins -> Marketplace`, and searching for `Bazel`.

**_Note:_** Starting from Android Studio 2024.2, Android Studio with Bazel plugin will be released from this project instead of https://github.com/bazelbuild/intellij/tree/google

Plugins compatible with **Stable** versions of Android Studio are released to the [Stable channel](https://plugins.jetbrains.com/plugin/9185-bazel-for-android-studio/versions/stable). The [Beta channel](https://plugins.jetbrains.com/plugin/9185-bazel-for-android-studio/versions/beta) is for  plugins compatible with **Canary** and **Beta** versions of Android Studio are released to the. Ways to install from the *Beta* channel:

* download and install them manually from the [Beta channel page](https://plugins.jetbrains.com/plugin/9185-bazel-for-android-studio/versions/beta) on JetBrains Marketplace.

* add the *Beta* channel to the IDE by navigating to `Settings -> Plugins -> Gear Icon -> Manage Plugin repositories` and adding `https://plugins.jetbrains.com/plugins/beta/9185`.

#### Compatibility with Android Studio
The version of the Android Studio with Bazel (ASwB) plugin will be the same as the build number of the Android Studio build it supports. For example, ASwB plugin version `12480590` is built for Android Studio `AI-242.23339.11.2422.12480590`.

### Build Plugin From Source
Building the ASwB plugin from this project requires some changes to the cloned repository because some of its dependencies are not publically available on AOSP.

1. Clone the repository:

    ```shell
    repo init --partial-clone -b studio-main -u https://android.googlesource.com/platform/manifest
    repo sync -c -j8
    ```

2. Checkout the branch/tag of the Android Studio release for which you want to build the plugin. List of branches/tags can be found [here](https://android.googlesource.com/platform/tools/adt/idea/+refs).
    ```shell
    repo forall -c git checkout {{release_branch}}
    ```

3. Modify `tools/adt/idea/aswb/platforms.bzl.OSS` to download the required version of Android Studio platform.

4. Copy `tools/adt/idea/aswb/platforms.bzl.OSS` to `tools/base/intellij-bazel/platforms.bzl` to be picked up by the WORKSPACE.

5. Create directory `tools/vendor/google/aswb/plugin_api` and copy `tools/adt/idea/aswb/plugin_api/BUILD.OSS` to `tools/vendor/google/aswb/plugin_api/BUILD` to set up the plugin API targets that the ASwB plugin depends on.

6. You can now build the Android Studio with Bazel plugin via
    ```shell
    bazel build //tools/adt/idea/aswb/aswb:aswb_bazel_zip --config=without_vendor --@//tools/base/intellij-bazel:intellij_platform=my_android_studio
    ```
    This will create a plugin zip file at `bazel-bin/tools/adt/idea/aswb/aswb/aswb_bazel.zip`, which can be installed directly to the IDE.


