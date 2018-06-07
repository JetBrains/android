# Resource Model

This document describes the “model” of resources used in Android Studio: the Resource Repository hierarchy, the `ResourceNotificationManager`,
and the `ResourceResolver`.

[TOC]

## The Old Resource Repository (`com.android.ide.common.resources.ResourceRepository`)

A ResourceRepository is, as the name suggests, a class which holds a set of resources.

The ResourceRepository is actually defined in tools/base, not Android Studio, and is quite old: it’s tied to the first versions of layoutlib
and the layout rendering in Eclipse. There were two repositories: one for framework resources (`android.R`), and one for the
user’s resources (`R`).

The new build system (Gradle) added a lot of new features around resource merging: you can provide multiple source sets, and these get
merged in a predefined way at build time. The old resource repository model was not up to the task for this, so a new version of
ResourceRepository was created, in the res2 package. However, the old one stays around because it’s still used to handle framework
resources (and for that specific purpose, it’s faster, which matters given that in a typical project, there are a lot of framework
resources (the list grows for every SDK release.)

The way the old system is still used is through `org.jetbrains.android.sdk.FrameworkResourceLoader.IdeFrameworkResources`. These objects are
created and cached through `ResourceResolverCache` (see below), keeping track of the API level. Usually the `FrameworkResourceLoader` is run
on a special `IAndroidTarget` that uses layoutlib resources bundled with the IDE, not the ones in `$ANDROID_HOME/platforms`. See
`StudioEmbeddedRenderTarget`.

## The New Resource Repository

The new resource repository system is based on  `com.android.ide.common.res2.AbstractResourceRepository` and
`com.android.ide.common.res2.ResourceItem`. Currently there are two use cases for it:

- `com.android.ide.common.res2.ResourceRepository` which is used by Lint,
- the whole hierarchy of different repositories used by the IDE.

These classes are closely related to `com.android.ide.common.res2.ResourceMerger` which is used by Gradle for merging resources between
source sets. `ResourceMerger` knows how to create `ResourceItems`. Some of the repositories used by the IDE are initialized by running
the merger on one or more directories to get the initial set of items.

## Resource Repositories

Whereas in non-Gradle projects, there is just a single resource repository modeling all non-framework resources, in a Studio Gradle project,
there is a hierarchy of resource repositories. The hierarchy computes the final set of resources taken from all sources sets, libraries
and the Gradle model (obtained at sync time).

Which one you should use depends on the context. Read the JavaDocs of the classes for details:
- [AppResourceRepository](AppResourceRepository.java): most common, aggregates the others.
- [ModuleResourceRepository](ModuleResourceRepository.java): resources defined in all resource folders of a given module.
- [ProjectResourceRepository](ProjectResourceRepository.java): resources defined in a module and all local libraries.

You can get instances by calling static factory methods of the relevant classes.

None of the repositories listed above actually "contain" any resources, they just combine values from other repositories, forming a tree.
The common superclass of all of them is [MultiResourceRepository](MultiResourceRepository.java).

All the values come from leaves in the tree, which are:
- [ResourceFolderRepository](ResourceFolderRepository.java): resources from a single folder inside the project.
- [FileResourceRepository](FileResourceRepository.java): resources from a single folder outside of the project (e.g. unzipped AAR).
- [DynamicResourceValueRepository](DynamicResourceValueRepository.java): values defined in `build.gradle` and passed through the model.

Another feature of the repository hierarchy is that children can invalidate caches in the parents. Currently we end up caching values
at multiple levels, because every `MultiResourceRepository` does caching of the final (merged) image of available resources.

See also the [`LocalResourceRepository` JavaDoc](LocalResourceRepository.java) for an additional description of how the system works.

## Lifecycle
Repositories from the first list above are singletons in the scope of a given module. The class responsible for this is `ResourceRepositories`
which is stored as user data on the `AndroidFacet` and has fields for all three kinds of module repositories.

`ResourceFolderRepositories` are unique per directory, managed by `ResourceFolderRegistry`. The registry uses 
`ResourceFolderRepositoryFileCacheService` to quickly save and load state (see section about blob files below).

`FileResourceRepositories` are unique per directory, managed by a soft references cache in the class itself.

## ResourceManager

IntelliJ had its own existing resource “repository”. This is the ResourceManager class, with its subclasses LocalResourceManager
(for project resources) and SystemResourceManager (for framework resources). These managers are used for resource lookup driven by the
editor machinery in IntelliJ, e.g. code completing @string/, resolving symbols, and so on.

Longer term, I’d like to rewrite all the editor features to be driven off of our resource repositories instead, and once that’s done,
remove these.

However, editor services need to be ready early after project startup, so it is important that ResourceRepositories initialize quickly.
And to do that:

## ResourceFolderManager

The ResourceFolderManager isn’t part of the resource repository hierarchy; however, it’s related so I’m describing it here.
The ResourceFolderManager is responsible for knowing which folders are involved in resource computations (e.g. the set of all res/
folders); it’s providing this set of folders to the resource repositories in a module, and it listens to things like root-change
events (e.g., after a GradleSync) to know when the resource folders have changed.

## ResourceNotificationManager

The ResourceNotificationManager is responsible for “listening” for resource changes for UI editors that care about re-rendering when
resources have changed. It is used by for example the new layout editor, and the theme editor. When these editors are made visible,
they register with the resource notification manager, and when they are hidden/closed, they unregister.

The ResourceNotificationManager watches for resource changes, figures out what changed, and then notifies clients. This allows for example
the layout editor to be rendered only when a dependent resource has changed.

## ResourceResolver

The `ResourceResolver` sits on top of the app resource repository and provides information about which specific resource values a given
resource reference should use. This means following all references until we end up with a "real" value.

This requires you to pick an actual “device” to render to: a device is represented by a `Configuration` class. The information in the
`Configuration` object picks a specific target API, density, screen orientation and so on, and based on that, the `ResourceResolver` will
decide which specific value for a resource is chosen when there are multiple choices. 

In the IDE, `ResourceResolver` instances are created and managed by `ResourceResolverCache`. Usually you get one by calling
`Configuration.getResourceResolver()`.

See also the class [JavaDoc](../../../../../../../../../base/sdk-common/src/main/java/com/android/ide/common/resources/ResourceResolver.java).

## ResourceHelper

Among other things, it can turn the final string value obtained from `ResourceResolver` into an awt `Image` or `Color`. It handles
`ColorStateList` and `StateListDrawable`, which is a layer on top of what `ResourceResolver` provides.

## AAPT2 Plans

AAPT2 is almost done. AAPT will support per-project namespaces. This means that we will need to start keeping track what's the namespace
name of some of its children.

On the other hand, the build system may eventually stop producing merged resource directories: instead it will need to calculate which
resources should be available in the APK and will just pass all the paths to aapt2. This is the same problem that the IDE solves already, so
will need to converge.

##  Speeding Up Resource Repository Initialization <a name="speeding_up_resource_repo_init"></a>

All the project resources are currently held in `ResourceFolderRepositories`. These are slow to initialize because all XML files must be
parsed into data structures. However, most of these are not used (e.g. all the non-picked translations of strings etc.)

### Lazy PSI Parsing

For efficiency, the file-based resource repositories parsers are used instead of PSI parsers for initialization. On initialization,
we still add PSI listeners and create stub PsiFiles for the listeners. As soon as the listener notices a non-trivial edit to a file,
we rescan it and replace the file-based resource items ([ResourceItem](ResourceItem.java)) for that file with PSI based ones
([PsiResourceItem](PsiResourceItem.java)). That way we gradually switch over from file based resources to PSI based resources, but only as
necessary, meaning the initial initialization of the repository is much faster. The plain file-based XML data structures are also more
memory efficient than the PSI-based ones.

Thus it is important that a ResourceItem can be used in place of a PsiResourceItem. Data Binding files are one case that are
not handled by the file-based parsers at all, and are handled by the PSI-based parser.

### Caching with Blob Files

Still, there remains a problem that the parser is opening many tiny files. To address this, Gradle has a fast persistence mechanism
for resources, which merges all of the data into a single "blob" XML file (see `ResourceMerger#writeBlobTo` and
`ResourceMerger#loadFromBlob`). This is used by Gradle for incremental builds. We reuse that mechanism to quickly persist resource
repositories. There is one cache file per ResourceFolderRepository.

The blob file format is currently XML, but in the future it would be nice to have a compressed binary format which may be smaller
and quicker to parse.

The XML blob has file nodes of the form:

```
  <merger ... xmlns:ns1="...xliff..."><dataSet ...><source path="/path/to/original/res">
    ...
    <file path="/path/to/original/res/values/some_values.xml" timestamp="12345">
      <string name="...">some\n  string<ns1:g ...>%1$s</ns1:g></string>
      <declare-styleable ...>
        <attr ...><enum .../>...</attr>
      </declare-styleable>
    </file>

    <file path="/path/to/original/res/layout-land/activity_foo.xml" timestamp="12345">
      <item name="activity_foo" type="layout"> ...</item>
      <item name="someId" type="id"/>
    </file>
    ...
  </source></dataSet></merger>
```

On reload, the blob loader checks that the `some_values.xml` has not been modified since the cached timestamp. Thus, init still involves
checking the last-modified times of many files. If enough files are stale, then the repository writes out a fresh blob file.
Filename-derived resources like drawable PNGs are not cached in the blob file. Instead, we simply get a directory listing and derive the
ResourceItem from the filename, to avoid checking timestamps and keep the size of the blob file small. A directory listing is also
required for XML-based resources to discover new files.

The [ResourceFolderRepositoryFileCache](ResourceFolderRepositoryFileCache.java) manages the storage for these blob files.  It maintains
an LRU list of projects and evicts the oldest project's files once there are "too many" projects. This class also handles invalidation:
if the version of the cache is different from expected, or if the user invokes the "Invalidate Caches" IDE action.

A developer must bump the expected version to invalidate the cache as needed. For example, if the ResourceFolderRepository is expected to
track more information (e.g., a new type of ResourceValue, or source XML line numbers for each item) and an old cache would be incomplete.

We may be able to extend this caching to FileResourceRepository as well, but that is currently not done. There are some differences
between the repositories. For example, FileResourceRepository stores ID items in a simple R.txt file instead of scanning layout, drawable,
etc. XML files for `android:id=@+id/foo` attributes.

### Parallel Initialization

Even with these optimizations, each ResourceFolderRepository initialization can still involve much I/O, especially on first run. For
projects with many res/ folders, a `PopulateCachesTask` can be invoked on project startup to initialize separate res/ folders in parallel.


## Value resources and the style system

Here are some notes about how the style system works and how it's encoded in the APK. A good way to inspect value resources is to use
`aapt dump --values resources path/to/my.apk`. Value resources are the ones stored in the resources.arsc table, not as files in the APK.

When the app runs, all resources are referenced using numeric IDs, not human readable names. Simple resources like strings or colors are 
assigned an ID and their value (which may be a reference to another resource ID) is stored in the resource table. You can read them using
e.g. `getResources().getColor(R.color.my_shade_of_blue)`. IDs for framework ("android") resources start with 0x01, IDs for app resources 
(in the old world without namespaces) start with 0x7f.

Attributes are keys in the key-value mapping that styles are all about. Every attribute has a unique identity (which ends up encoded as the
numeric ID) and a format: the set of types allowed for values stored under the given attribute. You can think of an attribute declared as
`<attr name="my_color" format="color" />` as something similar to `static Key<Color> myColor = new Key<Color>()` in the IDE java code. 
Attributes can be declared independently of styles and styleables, you can also use attributes already declared by the platform.  Attributes
don't store any values, their job is to have an identity under which values can be stored.

Styles are maps. A given style maps attributes to values (again, the value can be a reference to another resource). Styles may have parent 
styles, in which case a lookup is done in the parent if the current style doesn't define a value for a given attribute. This works similar
to how JavaScript property lookup works, if that helps.

A theme is a property of a context (e.g. an activity). Theme is a style. In the declaration of an activity you choose which style will be 
used as the theme for this activity.

A styleable is a collection of attributes. The XML syntax of `<declare-styleable>` lets you declare attributes "inline" when declaring the
styleable, but you can declare all the attributes upfront and just reference them by name inside the styleable declaration (or you can use
platform attributes). For example:

```xml
<resources>
  <attr name="my_name" format="string" />

  <declare-styleable name="HasNameAndColor">
    <attr name="my_name" />
    <attr name="android:color" />
  </declare-styleable>
</resources>

```

A styleable is used to look up all its attributes at once in a style (most often the theme). In reality styleables are just arrays of ints,
where the values are IDs of attributes. They are not stored in resources.arsc but in the R class. There's also no corresponding Java type,
you can create the table of attribute ids on the fly and pass it to Theme.obtainStyledAttributes. Styleables just make it a bit easier to 
keep track of the order in which the attributes are stored in the array, because the R class contains helpful constants for them:

```java
class R {
  class attr {
    public static final int my_name = 0x7faaaaaa;
    // ...
  }
  class styleable {
    public static final int[] HasNameAndColor = {
      0x7faaaaaa, // my_name
      0x01bbbbbb  // android:color
    };

    public static final int HasNameAndColor_my_name = 0; // Index in the array above.
    public static final int HasNameAndColor_android_color = 1; // Index in the array above.
  }
  // ...
}
```
