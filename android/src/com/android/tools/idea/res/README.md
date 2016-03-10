# Resource Model

This document describes the “model” of resources used in Android Studio: the Resource Repository hierarchy, the ResourceNotificationManager,
and the ResourceResolver.

[TOC]

## The Old Resource Repository

A ResourceRepository is, as the name suggests, a class which holds a set of resources.

The ResourceRepository is actually defined in tools/base, not Android Studio, and is quite old: it’s tied to the first versions of layoutlib
and the layout rendering in Eclipse. There were two repositories: one for framework resources (`android.R`), and one for the
user’s resources (`R`).

The new build system (Gradle) added a lot of new features around resource merging: you can provide multiple source sets, and these get
merged in a predefined way at build time. The old resource repository model was not up to the task for this, so a new version of
ResourceRepository was created, in the res2 package. However, the old one stays around because it’s still used to handle framework
resources (and for that specific purpose, it’s faster, which matters given that in a typical project, there are a lot of framework
resources (the list grows for every SDK release.)

The rest of this document deals strictly with res2.ResourceRepository.

## The New Resource Repository

The new resource repository, res2.ResourceRepository, is used by the Gradle build system, and is used to implement Gradle’s model of
resources around merging.

It is also the base class for the ResourceRepository used in AndroidStudio, but it has been subclassed in AndroidStudio, since some of the
behaviors in the base ResourceRepository are not suitable for use in an IDE. For example, the Gradle version will throw exceptions if
duplicate keys are found, since that’s a build error - but this is a normal state in the IDE where you for example might have duplicated a
line and you’re about to edit the key to something new.

## Resource Repositories

Whereas in non-Gradle projects, there is just a single resource repository modeling all non-framework resources, in a Studio Gradle project,
there is a hierarchy of resource repositories.

Which one you should use depends on the context.

### AppResourceRepository

The most commonly used one is the [AppResourceRepository](AppResourceRepository.java): `AppResourceRepository.getAppResources(module, true)`

This repository gives you a merged view of all the resources available to the app, as seen from the given module, with the current variant.
That includes not just the resources defined in this module, but in any other modules that this module depends on, as well as any libraries
those modules may depend on (such as appcompat).

When a layout is rendered in the layout, it is fetching resources from the app resource repository: it should see all the resources just
like the app does.

### ModuleResourceRepository

If you want just the resources defined in a specific module, you can look up the [ModuleResourceRepository](ModuleResourceRepository.java)
for the given module: `ModuleResourceRepository.getModuleResources(module, true)`

Note that this does not include resources that this module depends on!

### ProjectResourceRepository

If you want all the resources defined in a module, as well as all the modules it depends on, but not external libraries, you can use the
[ProjectResourceRepository](ProjectResourceRepository.java): `ProjectResourceRepository.getProjectResources(myModule, true)`

This repository lets you look up all “local” resources defined by a user.

An example of where this is useful is the layout editor; in its “Language” menu it lists all the relevant languages in the project and
lets you choose between them. Here we don’t want to include resources from libraries; If you depend on Google Play Services, and it
provides 40 translations for its UI, we don’t want to show all 40 languages in the language menu, only the languages actually locally in
the user’s source code.

## The Resource Repository Hierarchy

The above repositories are the ones you’ll deal with as a developer on Android Studio; normally you’ll grab the `AppResourceRepository`,
but as explained above there are cases where you want one of the others.

### DynamicResourceValueRepository

The Gradle plugin allows resources to be created on the fly (e.g. you can create a resource called build_time of type string with a value
set to a Groovy variable computed at build time). These dynamically created resources are computed at Gradle sync time and provided via
the Gradle model.

Users expect the resources to “exist” too, when using code completion. The [DynamicResourceValueRepository](DynamicResourceValueRepository.java)
makes this happen: the repository contents are fetched from the Gradle model rather than by analyzing XML files as is done by the other
resource repositories.

### MultiResourceRepository

The [MultiResourceRepository](MultiResourceRepository.java) is a super class for several of the other repositories; it’s not really used on
its own. Its only purpose is to be able to combine multiple resource repositories and expose it as a single one, applying the “override”
semantics of resources: later children defining the same resource type+name combination will replace/hide any previous definitions of the
same resource.

In the resource repository hierarchy, the MultiResourceRepository is an internal node, never a leaf.

### FileResourceRepository

The [FileResourceRepository](FileResourceRepository.java) on the other hand is a “leaf node” in the resource repository hierarchy:
it always represents a concrete `java.io.File` directory. This is used for resources that do not change, e.g. are not editable by the user.
This currently means AAR resources such as the appcompat library’s res folder. This is more efficient than using IntelliJ's PSI based
XML parsers (discussed next).

The implementation of the FileResourceRepository is mostly directly using the same implementation as the Android Gradle plugin’s resource
handler, so it’s fast & accurate.

### ResourceFolderRepository

The [ResourceFolderRepository](ResourceFolderRepository.java) is another leaf node, and is used for user editable resources (e.g. the
resources in the project, typically the res/main source set.) Each ResourceFolderRepository contains the resources provided by a single res
folder. This repository is built on top of IntelliJ’s PSI infrastructure. This allows it (along with PSI listeners) to be updated
incrementally; for example, when it notices that the user is editing the value inside a <string> element in a value folder XML file, it will
directly update the resource value for the given resource item, and so on.

For efficiency, the ResourceFolderRepository is initialized via the same parsers as the FileResourceRepository and then lazily switches to
PSI parsers after edits. This is discussed more in a [later section](#speeding_up_resource_repo_init).

## ResourceManager

IntelliJ had its own existing resource “repository”. This is the ResourceManager class, with its subclasses LocalResourceManager
(for project resources) and SystemResourceManager (for framework resources). These managers are used for resource lookup driven by the
editor machinery in IntelliJ, e.g. code completing @string/, resolving symbols, and so on.

Longer term, I’d like to rewrite all the editor features to be driven off of our resource repositories instead, and once that’s done,
remove these.

However, editor services need to be ready early after project startup, so it is important that ResourceRepositories initialize quickly.
And to do that:

##  Speeding Up ResourceRepository Initialization <a name="speeding_up_resource_repo_init"></a>

All the project resources are currently held in ResourceFolderRepositories. These are slow to initialize because all XML files must be
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

The ResourceResolver sits on top of the app resource repository and provides information about which specific resource values a given
resource reference should use. This requires you to pick an actual “device” to render to: a device is represented by a Configuration class.
The information in the Configuration object picks a specific target API, density, screen orientation and so on, and based on that,
the ResourceResolver will decide which specific value for a resource is chosen when there are multiple choices.

There is also a ResourceResolverCache which is used to allow resource resolvers to be reused such that they don’t have to be constructed
over and over again for the same sets of configurations.

## AAPT2 Plans

AAPT2 is almost done. AAPT will support per-project namespaces. We’ll need to update the resource repository mechanism and lookup to deal
with this (right now resources are handled in a binary way: framework or not). We’ll need to make the resource merging aware of
namespaces etc.

