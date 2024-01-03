About Patch files
====

As described in the commit message of [support-k2-registrar-for-1.5.8-k2.0.0-Beta1-ee81c93ee74.patch](support-k2-registrar-for-1.5.8-k2.0.0-Beta1-ee81c93ee74.patch),
we prepared [aosp/2895997](aosp/2895997) and compose compiler plugin jar files
[ab/P67557495](ab/P67557495) by
updating [19th PatchSet of Ic40e3cb8872a6b5edfa56c6d8973ae9860eef74c](aosp/2833535)
to support the K2 extension registrar:

* [support-k2-registrar-for-1.5.8-k2.0.0-Beta1-ee81c93ee74.patch](support-k2-registrar-for-1.5.8-k2.0.0-Beta1-ee81c93ee74.patch): A patch that we can apply to the androidx upstream (git hash: 5aea0a4de4f).
* [diff-from-1.5.8-k2.0.0-Beta1-ee81c93ee74.patch](diff-from-1.5.8-k2.0.0-Beta1-ee81c93ee74.patch): A patch that shows the updates we added from the 19th PatchSet of Ic40e3cb8872a6b5edfa56c6d8973ae9860eef74c.
  * Note that we updated 5 files:
    * ComposePlugin.kt and org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar for K2 registrar support
    * Other changes to fix presubmit failures on AndroidX repo
