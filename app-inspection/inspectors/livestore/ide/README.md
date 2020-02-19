This is a demo inspector for the LiveStore library.
See also: //tools/base/app-inpsection/demo/livestore

# Running the Demo

By default, the demo is not hooked up, as we don't want to ship it in
production. In order to see the demo, you'll need to

1. Build all demo inspector resources using bazel.
   * `$ bazel build //tools/base/app-inspection/demo/livestore/...`
1. Open `//tools/adt/idea/android-plugin/src/META-INF/plugin.xml`
1. Add an `<xi:include>` tag targeting `app-inspector-livestore.xml`
1. Add a module dependency on `app-inspection.inspectors.livestore.ide` from
   the `android-plugin` module.
1. Make sure the App Inspection Tool Window is enabled.
  * At the current time, this feature is gated behind a flag, which will
    likely be removed in or around April 2020.
  * You can add "-Dappinspection.enable.tool.window=true" to your VM
    options to enable the flag via command line.
  * If you are reading this after the flag is removed, please delete the parent
    list item.
1. Run `Android Studio` and open
   `//tools/base/appinspection/demo/livestore/app/BreakoutClone`
1. Run the app.
1. Open the `App Inspector` by running `View > Tool Windows > App Inspection`
   * This may temporarily be called `Database Inspector` for legacy reasons, if
     you're opening this up before we have more than one official inspector.

# Objectives

This demo is meant to serve as a starting point for other app inspectors,
with recommendations for how you might:

* separate your model logic and UI components.
* test your code.
* handle both fetching all data when the inspector first connects followed by
  incremental updates.
* keeping UI settings consistent across multiple runs
* overall, showcasing fairly realistic inspector features.

The demo inspector is built out of two separate modules:
* *model* - the logic for handling and organizing data from the device
* *view* - UI components and IDE extensions

It is not meant to be turned on in production, but can be enabled in
development mode by enabling its flag.

