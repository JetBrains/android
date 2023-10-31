Understanding IDE plugin configuration files (plugin.xml)
===

Prerequisite: first read through JetBrains' documentation on plugin.xml files
[here](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html).


Overview
---

Every IntelliJ plugin has exactly one `plugin.xml` file. It specifies plugin metadata
(name, ID, vendor) and lists extension classes that IntelliJ should instantiate at runtime.
The `plugin.xml` file for the Android plugin currently resides at
`android-plugin/descriptor/resources/META-INF/plugin.xml`.


Module-local configs
---

Since the Android plugin is large, we split it up into multiple modules. Generally, each module
should have its own plugin config file. For example, the `intellij.android.dagger` module
has a config file at `dagger/src/META-INF/android-dagger.xml` referenced by the top-level
`plugin.xml` file like so:
```xml
<xi:include href="/META-INF/android-dagger.xml"/>
```

When creating a new plugin config file, use a unique name to avoid
conflicts when multiple modules are merged into the same release jar.


Testing
---

Studio unit tests generally run with only a subset of the Android plugin on the classpath.
This is beneficial because:
* we can compile fewer modules before running the test, and
* Bazel can more frequently reuse cached test results.

We still use the production `plugin.xml` in unit tests. To activate it, make sure you
have a transitive runtime dependency on the `intellij.android.plugin.descriptor` module.
The test classpath automatically determines which module-local config files are loaded. Any
unresolved `<xi:include>` tags in `plugin.xml` are gracefully ignored in unit test mode.

When writing tests, prefer relying on production config files rather than manually
registering extensions in the test itself. Otherwise, your test may pass even if there are bugs in
your config file. Similarly, when testing a specific extension class, consider retrieving an
instance from the corresponding extension point rather than instantiating your class directly.

If you find that an extension is unavailable in your test, review your module dependencies
to make sure the classpath includes the config files you expect.


Module isolation
---

Plugin config files should register extension classes only from the current module (preferred)
or a dependency of the current module. Otherwise, downstream test targets may need to add
spurious runtime dependencies in order to avoid `ClassNotFoundException` when the plugin
configuration file is loaded. We have a Lint check called `PluginXmlDetector` to enforce this.

Ideally, extensions should be registered in the same module as the extension class. That way,
downstream clients can trust that all classes accessible on the classpath have been properly
registered. The DevKit plugin has an inspection called `PluginXmlDomInspection` to encourage this.


Conditional configuration based on IDE type (Studio vs. IntelliJ)
---
The Android plugin is released in both Android Studio and IntelliJ. Sometimes it is useful
to configure the Android plugin differently in these two cases. For example, JetBrains
configures the Android plugin to show fewer Android-related actions in the
main toolbars when running inside IntelliJ.

You can use the [optional plugin dependency](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#optional-plugin-dependencies)
mechanism to conditionally include a config file based on the underlying IDE type. Here's
an example:
```xml
<depends optional="true" config-file="android-plugin-androidstudio.xml">com.intellij.modules.androidstudio</depends>
```
Note that `com.intellij.modules.androidstudio` is a marker module actived only in
Android Studio. By convention we use the `androidstudio` suffix when naming config
files loaded only in Android Studio.


Misc tips
---

* Plugin extension classes are often instantiated lazily. If you misspell a class name, you
  might not see a runtime error immediately.

* If IntelliJ encounters problems in a `plugin.xml` file, it will sometimes log warnings
  in `idea.log` rather than throw an exception outright.
