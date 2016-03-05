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

If you want just the resources defined in a specific module, you can look up the [ModuleRepository](ModuleRepository.java) for the given
module: `ModuleResourceRepository.getModuleResources(module, true)`

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
This currently means AAR resources such as the appcompat library’s res folder, but since it’s more efficient than the PSI based repository
(discussed next) I’d like to switch to it for initial setup as well.

The implementation of the FileResourceRepository is mostly directly using the same implementation as the Android Gradle plugin’s resource
handler, so it’s fast & accurate.

### ResourceFolderRepository

The [ResourceFolderRepository](ResourceFolderRepository.java) is another leaf node, and is used for user editable resources (e.g. the
resources in the project, typically the res/main source set.) Each ResourceFolderRepository contains the resources provided by a single res
folder. This repository is built on top of IntelliJ’s PSI infrastructure. This allows it (along with PSI listeners) to be updated
incrementally; for example, when it notices that the user is editing the value inside a <string> element in a value folder XML file, it will
directly update the resource value for the given resource item, and so on.

## ResourceManager

IntelliJ had its own existing resource “repository”. This is the ResourceManager class, with its subclasses LocalResourceManager
(for project resources) and SystemResourceManager (for framework resources). These managers are used for resource lookup driven by the
editor machinery in IntelliJ, e.g. code completing @string/, resolving symbols, and so on.

Longer term, I’d like to rewrite all the editor features to be driven off of our resource repositories instead, and once that’s done,
remove these.

However, before we can do that we need to make the ResourceRepositories initialize quickly. And to do that:

## Speeding Up ResourceRepository Initialization

All the project resources are currently held in PSI based resource repositories. These are slow to initialize because all XML files must be
parsed into data structures. However, most of these are not used (e.g. all the non-picked translations of strings etc.)

The file-based resource repositories for non-changing resources are much cheaper.

It would be nice to initially initialize all project resources using the light-weight file based resources. We also add PSI listeners.
And as soon as we notice an edit to a file, we rescan it and replace the resource items for that file with PSI based ones.
That way we gradually switch over from file based resources to PSI based resources, but only as necessary, meaning the initial
initialization of the repository is much faster.

Furthermore, Gradle already has a fast persistence mechanism for resources. This is used for incremental builds. We should reuse this to
quickly persist resource repositories.

## ResourceFolderManager

The ResourceFolderManager isn’t part of the resource repository hierarchy; however, it’s related so I’m describing it here.
The ResourceFolderManager is responsible for knowing which folders are involved in resource computations (e.g. the set of all resource
folders); it’s providing this set of folders to the resource repositories in a module, and it listens to things like GradleSync to know
when the resource folders have changed.

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

