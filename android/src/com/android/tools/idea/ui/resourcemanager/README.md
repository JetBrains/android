
<!---  Source doc: https://docs.google.com/a/google.com/open?id=1hFWf2MO7SoQyfwCO27cZw6cbt-uMWH5K5KmAglpastw ----->


# Resource Manager

The resource manager is a tool to import, explore and use Android resources.

It is available as a tool window in Android Studio.


## Definitions
**Resource:** In this document a resource is considered to be a set of one or more files with a common type and a name in the Android world and represents a single semantic item. Each file composing a resource has a different set of qualifiers applied to it. In the Android project, they are referred to by a unique URL: “@package:type/name”.

**Qualifier**: a qualifier is a flag added to a version of a resource to define for which device configuration a resource will be used. The most common ones are the density qualifiers that define the target screen density for a resource. In the Android Project, a qualifier is set on a file by putting the file into a folder with a specific set of suffixes (e.g drawable-xxhdpi/ contains the drawable resources that will be displayed on a XXHDPI device)

**Version:** a version of a resource is a single file composing a resource with a set of qualifiers applied to it. For example, let’s say we have a resource named _avatar_ and composed of multiple files for different density: _drawable-mdpi/avatar.png, drawable-xhdpi.png_. In that case, the file _drawable-mdpi/avatar.png_ is considered to be a version of the _avatar_ resource.

**LayoutLib:** the Android Studio implementation of the Android rendering library. It is used to render Android specific resources like vector drawable and layout files.


## Goals

### Logical View of Android Resources
The first goal is to provide a logical view of the Android resource system. 

Resources in Android are divided into different folders depending on the type but also the target configuration (qualifiers).

From the user point of view, it is hard to manually manage resources that represent the same object but are located in different locations.

The resource manager aims to group the resources by name and give an overview of the resources available and a level 2 view that displays the different versions of a resource.


### Preview of the resources
Some resources cannot be displayed by the OS file explorer and have to be manually opened within Android Studio to be displayed. The resource manager leverages LayoutLib to render those resources (Layout, VectorDrawable, ...) and provide thumbnail views.


### Importing 
Because of the complex structure of the res/ directory, it is cumbersome for the user to import a resource composed of multiple files and even more if these files need to be  converted into a format supported by Android.

Resource Manager abstract the file structure on disk by automatically converting files and copying them in the correct folder depending on the qualifiers.

It has been built with the idea that the importation feature could be extended via plugins to support the importation of more types of resources.

### Plugins
As described in more details below, the variety of resources that exist in Android or can potentially be converted into a format understood by Android is huge. 

The resource manager has been created with the idea that in the future, we could allow third party developer to add support for more resources


## The Resource System

The resource manager is heavily based on top of the existing (and complex!) resource system of Android Studio. An amazing documentation is available at [studio-master-dev/android/src/com/android/tools/idea/res/README.md](https://googleplex-android.git.corp.google.com/platform/tools/adt/idea/+/refs/heads/studio-master-dev/android/src/com/android/tools/idea/res/README.md) and is really worth reading.


## Design

### Overview
The project has been designed with the following principles in mind that will be described later:

*      Being UI heavy, we use an MVVM architecture
*      We use a dependency injection pattern to ensure a good testability and reusability
*      We want to be able to extend it through IntelliJ plugins


### The MVVM architecture
When Googling MVVM, most of the results refer to MVVM in Android which uses data binding. With Swing, we don’t have a databinding framework so the implementation of MVVM differs from what one could find on the first Google results.

The key concept in MVVM is that the View knows about the ViewModel, the ViewModel knows about the Model and this is unidirectional. The ViewModel can notify the View to update via callbacks.


In the project, all ViewModels are suffixed by “ViewModel” and have a prefix similar to the name of the view. For example, if a UI bug is seen on the importation dialog, a good start is to look into ResourceImportDialog.kt. On the other hand if a bug happens on the backend part of the import flow (e.g a qualifier is missing on an imported file), one should start looking into ResourceImportDialogViewModel.

The View deals with user interactions and UI and should hold (almost) no logic. The logic is delegated to the ViewModel whose main role is to get the data from the Model and shape it for the View.

In the Resource Manager project, the underlying Resource System is considered to be the main model. On top of that, the `DesignAsset` and `DesignAssetSet` are abstraction models made for the Resource Manager in order to ease the manipulation of resources data.


### Dependency Injection
We tried to use a dependency injection pattern as much as possible. There is no framework involved here whatsoever, the idea is that when an object handling some specific logic is needed, it is passed as a parameter in the constructor. In other words, we avoid instantiating complex classes within classes.

The main advantage of this approach is that if we want to use a test  a class A that depends on a class B, but B is complex and we don’t care about testing it, it becomes easy to replace B with a stub implementation of the same interface.

### DesignAssets
Because we want kee the Resource System and the Resource Manager separated, we introduced an abstraction to represent the resources within the Resource Manager called DesignAsset.

A `DesignAsset` is made of a name, a resource type, a list of qualifiers and a file (_and optionally a ResourceMergerItem that is used to ease the interface between the Resource Manager and the Resource System but in general, we don’t need it)_.

A `DesignAsset` represents a version of a resource, which can be in the user’s project or not. As soon as a file enters the Resource Manager pipeline, it is wrapped into a DesignAsset. `DesignAsset` are then grouped into a DesignAssetSet (I should work on my naming) which represent a resource in the AndroidWorld. A DesignAssetSet is simply a name and a list of DesignAsset. The name of the DesignAssetSet is the one used in the Android project and overrides the names of its DesignAssets. 

In the current implementation, DesignAssets only represent image resources and would need to be modified to represent Color or textural data.


### Rendering Pipeline
Because of the diversity of resources supported by Android, the resource manager tries to separate the rendering pipeline of a resource with the rest of the system. It aimed to be easily extensible.

At the lowest level of the pipeline lives the `DesignAssetRenderer` interface. Implementations of this interface define the type of file they can render, take a `DesignAsset` as the input and return an Image. The way the images are rendered should be totally abstracted to the outside world.

Each renderer is registered as an IntelliJ extension on the extension point `_com.android.resourceViewer`_. 

The entry point to the pipeline is via `DesignAssetRendererManager`

### Importation Pipeline
Similar to the rendering, the importation is made from different importers depending on the file type. There are currently three main ones: RasterResourceImporter, SVGImporter, VectorDrawableImporter. Each importer is an intellij extension and is registered on the `com.android.resourceImporter` extension point. The goal of having them as extensions is to abstract the conversion mechanisms from the resource manager and allow easier support of more input format. 

One very interesting format to support would be Zip files. In order to do that, the ResourceImport interface would need to be slightly modified to take return multiple DesignAsset in ResourceImporter.processFiles.

More details are available as Javadoc in each importer.  

### Drag and Drop, Paste, DataContext
The ResourceDataManager file is the main glue between the resource manager and the rest of the IDE when dealing with transfer of data.

Drag and Drop in Swing uses Transferable and DataFlavor to provide data across different UI Components. The resource manager defines the `RESOURCE_URL_FLAVOR` data flavor which returns a `ResourceUrl`. This is enough to identify a resource and generate the correct text when inserting a reference to this resource.

IntelliJ relies a lot on DataContext that can be fetched by anything from the currently focused UI component, for example the refactor menu knows about the currently selected file via the DataContext. To enable the refactor action on the resource manager, some UI components inherit from DataProvider and then delegate the shaping of the `DesignAsset` into some IntelliJ classes (like PsiElement) via the `ResourceDataManager`.


 > Little tip on getting more info of the IntelliJ specific behaviors: use the keyword “IntelliJ sdk” along with what you are looking for to get the result from the (pretty good) developers documentation of IntelliJ.

The `ResourcePasteProvider` is an implementation of the IntelliJ’s PasteProvider and intercept any attempt to paste a Transferable with a RESOURCE_URL_FLAVOR. This allows us to be smart about what we paste. For example, if the resource url is an ImageView, the full XML for an ImageView will be generated instead of just pasting the URL.

The Drag and Drop with the layout editor is handled in DnDTansferItem, which also knows how to interpret the RESOURCE_URL_FLAVOR.
