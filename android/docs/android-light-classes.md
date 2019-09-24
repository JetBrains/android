# Android light classes

Note: Android light classes are only loosely related to "Kotlin light classes" (see
[kotlin-light-classes.md](kotlin-light-classes.md)). Both mechanisms borrow the name from `LightElement`, which is a supertype for PSI
elements not backed by actual source code (i.e. not created by a `PsiParser`), but implement the idea in very different ways.

## Background
A common pattern in Android development is to expose various assets or "resources" to the app's code via classes generated at build time.
This idea has been used from the earliest days of Android, with the original `aapt` tool generating app's `R` class based on contents of the
`res` directory. It has been later adopted by Data Binding and View Binding and is considered for more libraries in the future. Usually the
build-time component is implemented in the Android Gradle Plugin, but it could also be an annotation processor or a separate Gradle plugin.

From the IDE point of view this approach is problematic, since additional code is generated at build time and, until this code is written to
disk, standard mechanisms like code completion etc. are not aware of the generated classes. This means that without additional support from
the IDE, these features are not usable before the first build. Even after the first build, additional changes are not reflected in code
completion until the next build. This is not a good user experience considering the current latency of (even incremental) builds.

This is why Android Studio tries to simulate known code generators in real time, as the user modifies their inputs. For instance, it
maintains an up-to-date repository of all known resources (built by parsing XML files) and uses it to create "fake" classes injected
directly into editor mechanisms like code completion, reference resolution etc.

## Limitations
To properly implement the "light classes idiom" for a code generator, these requirements have to be met:

1. IDE needs to understand the API of generated classes. This means the logic for picking the class name, its methods, and fields has to
   be relatively simple since it has to be duplicated between the IDE and the code generator. In practice this also means it cannot change
   too often.
2. IDE needs to have an efficient way of computing all required information from the input files and keeping that information up-to-date.
   *Note: This might be less information than what the full code generator needs. For example, the IDE only needs to know about the class's
   API, not its method bodies or field values.*
3. IDE needs to know if the feature should be enabled at all or not. For instance `R` classes are generated for all Android modules, but
   the Data Binding support runs only in modules that enabled Data Binding in their `build.gradle` files. This information is usually stored
   in the Gradle model, obtained by the IDE at sync time.

## Implementation details
Implementing a feature based on light classes means "tricking" the IDE into believing certain classes exist, even though they are not
defined in any source files of the project. This means several core editor mechanisms need to be extended using the typical IntelliJ
approach of registering extensions for platform extension points. Support for a build time code generator means implementing the following
components:

### Model
A mechanism to quickly determine what classes should be made available. This part is what varies most between different features that use
light classes, since the logic is specific to the feature. Other components described below are IDE extension points that we discovered
over time that had to be implemented for light classes to work. But to know what logic to put in those extension points, the IDE needs to
have an understanding of the code generator semantics and relevant input files that affect the generated code.

The only requirement for the model code is that extension points described below need a way of calling into it, which means usually it ends
up being an IntelliJ module service. In the case of `R` classes we use a whole IDE subsystem of resource repositories
(`ResourceRepositoryManager`) that are used by per-build-system implementations of `LightResourceClassService`. For Data Binding we have a
custom IntelliJ index (`BindingXmlIndex`) which is used from `ModuleDataBinding`.

It's important to avoid unnecessary work for modules that don't use the code generator in question. Another aspect to consider is memory
usage, avoiding memory leaks and correctly disposing any model information when the module or project are closed.

### `PsiClass` Implementation
Representation of the generated classes that will be passed to IntelliJ platform APIs to enable editor features like code completion etc.
This is usually a subclass of `AndroidLightClassBase` with the right implementation of `getFields()`, `getMethods`(), `getInnerClasses()`
and related methods.

### `PsiElementFinder`
Extension point used by `JavaPsiFacade` to find classes and packages based on their fully qualified names. This ends up called by reference
resolution code, meaning references to generated classes are not highlighted in red.

### `ResolveScopeEnlarger`
To be considered for reference resolution and code completion, the new classes have to be in the right scope, usually the "resolve scope" of
the files in which they are used. Light classes live in light virtual files which live in a light virtual file system, which means they are
not part of the project. To fix this, we provide implementations of `ResolveScopeEnlarger` and `KotlinResolveScopeEnlarger`.

### `PsiShortNamesCache`
Extension point used by code completion for unqualified class names and for suggesting imports. It needs to be implemented for the light
classes to behave correctly.

### `GotoDeclarationHandler` or `getNavigationElement`
Light classes and their members don't have corresponding source code, so calling "go to declaration" on code referencing them will by
default display a small balloon saying "cannot find destination" or something similar. Typically these light elements "represent" some other
files in the project, so we override "go to declaration" to open these other files instead. This can be done either by overriding 
`getNaviationElement` on the light classes/fields/methods or by providing a custom `GotoDeclarationHandler` extension point, if more context
is needed or a completely different behavior (other than opening a `PsiElement` in a code editor) should be used.

### Modifying AGP model
To avoid duplicate definitions of light classes (some of them out-of-date), sources generated at build time cannot be included by the IDE as
project sources. This means they either have to be not in the source set model at all or excluded at sync time based on other fields in
the model.
