# Rendering Class Loading & ModuleClassLoader

High-fidelity rendering of Android UI on a standard JVM requires a sophisticated class loading architecture to isolate project code, apply platform-specific transformations, and maintain performance.

## The Core Concept: ModuleClassLoader

Each rendering task in Android Studio operates within its own `ModuleClassLoader`. This loader is responsible for loading all non-framework classes (Custom Views, Composable functions, and their dependencies) for a specific Android project module.

### Why a Custom ClassLoader?

1.  **Isolation**: Prevents project-specific dependencies (e.g., a specific version of a library) from conflicting with the IDE's own internal dependencies.
2.  **Bytecode Transformation**: Before a class is defined, it can be modified on-the-fly to adapt Android-specific behaviors for the JVM.
3.  **Fast Refresh**: Instead of restarting the whole IDE, the system can simply discard the old `ModuleClassLoader` and create a new one to reflect code changes.

## Bytecode Transformations (ClassTransforms)
([`src/com/android/tools/rendering/classloading`](../src/com/android/tools/rendering/classloading))

The classloader applies a pipeline of transformations using ASM. Key transforms include:

| Transform | Purpose |
| :--- | :--- |
| **`ViewMethodWrapperTransform`** | Wraps certain framework methods (like `View.getContext()`) to return mocks or specialized implementations. |
| **`CooperativeInterruptTransform`** | Injects checks for thread interruption (`Thread.interrupted()`) into loops and method entries, allowing for safe cancellation of hanging renders. |
| **`MethodInterceptTransform`** | Redirects specific method calls to alternative implementations (e.g., redirecting `Thread.start()` to prevent user code from spawning unmanaged threads). |
| **`PreviewAnimationClockMethodTransform`** | Redirects animation clock calls to the preview's stepping clock for frame-by-frame control. |
| **`RenderActionAllocationLimiterTransform`** | Injects byte-allocation tracking to prevent `OutOfMemoryError` caused by malicious or buggy custom views. |
| **`SdkIntReplacer`** | Dynamically replaces `Build.VERSION.SDK_INT` calls to match the target device configuration. |
| **`ResourcesCompatTransform`** | Ensures compatibility between project resources and the Layoutlib resource resolution system. |

## View Loading and Instantiation
([`ViewLoader.java`](../src/com/android/tools/rendering/ViewLoader.java))

The `ViewLoader` orchestrates the actual instantiation of view classes.
- **Recursion Guard**: It maintains a counter to prevent `StackOverflowError` if a custom view attempts to nest itself indefinitely (limited to 100 levels).
- **Fallback Mechanism**: If a class fails to load (e.g., due to a compilation error), `ViewLoader` can attempt to instantiate a **Mock View** or a superclass to allow the rest of the layout to render, preventing a total preview failure.
- **R Class Parsing**: It can load R classes via bytecode parsing (silently) to ensure resource references are resolved correctly without needing a full compilation in some matching cases.

## DelegatingClassLoader
([`classloading/loaders/DelegatingClassLoader.kt`](../src/com/android/tools/rendering/classloading/loaders/DelegatingClassLoader.kt))

This base class allows flexible class loading strategies without strict inheritance chains.
- **Renamed Classes**: It handles cases where a transformation renames a class (e.g. `MyClass` -> `MyClass_Original`). If the code tries to load `MyClass`, the loader understands the mapping and returns the already-loaded transformed version.

## StudioModuleClassLoader
([`StudioModuleClassLoader.java`](../../android/src/org/jetbrains/android/uipreview/StudioModuleClassLoader.java))

`StudioModuleClassLoader` is the primary implementation used in Android Studio. It integrates with the IDE's project model and provides:

### 1. Fast Preview Integration
When **Fast Preview** is enabled, the `ModuleClassLoader` works with the `FastPreviewManager` to load "hot" versions of Composable functions directly from memory, bypassing the standard Gradle build path.

### 2. Up-to-date Verification
To avoid unnecessary re-initialization, the classloader tracks its state through:
-   **`isUserCodeUpToDate`**: Uses PSI (Program Structure Interface) change tracking to detect if the source code of the module has changed.
-   **`areDependenciesUpToDate`**: Checks if binary dependencies (libraries, SDKs) have changed.

## Standalone Class Loading (Screenshot Testing)
([`StandaloneModuleClassLoaderManager.kt`](../../../../base/standalone-render/lib/src/com/android/tools/render/StandaloneModuleClassLoaderManager.kt))

For headless environments like screenshot testing (`previewscreenshot`), the system uses a specialized implementation:
-   **`DefaultModuleClassLoader`**: A lighter-weight implementation that delegates to `UrlClassLoader`.
-   **Differences from Studio**:
    -   Does NOT use the full PSI-based invalidation (assumes static classpath for the test run).
    -   Applies a subset of transformations (mainly `ResourcesCompatTransform` and `SdkIntReplacer`).
    -   Uses a single-threaded `Preloader` instead of the complex Hatchery.
-   **Usage**: It allows running Compose previews and Layoutlib rendering without the full IntelliJ IDEA environment.

## ModuleClassLoaderManager & Hatchery
([`ModuleClassLoaderManager.kt`](../src/com/android/tools/rendering/classloading/ModuleClassLoaderManager.kt))

Managing these classloaders is resource-intensive. `ModuleClassLoaderManager` provides a reference-counted registry and caches loaders based on their configuration.

### The Hatchery (Pre-warming)
([`ModuleClassLoaderHatchery.kt`](../../android/src/org/jetbrains/android/uipreview/ModuleClassLoaderHatchery.kt))
To minimize the "First Render" latency, the **Hatchery** system pre-warms classloaders in the background:
-   **Triggering**: When a `ModuleClassLoader` is requested but not available, the Hatchery records the requirement.
-   **Incubation**: It maintains a "clutch" of pre-initialized classloaders.
-   **Pre-loading**: While sitting in the hatchery, a classloader can pre-load heavy library classes (like the Compose runtime) so they are ready for immediate use.

## Troubleshooting & Diagnostics
([`ModuleClassLoaderDiagnostics.kt`](../src/com/android/tools/rendering/classloading/ModuleClassLoaderDiagnostics.kt))

The system tracks class loading performance and failures:
-   **Load Times**: Metrics on how long it takes to find and transform classes.
-   **`ClassBinaryCache`**: ([`ClassBinaryCache.kt`](../src/com/android/tools/rendering/classloading/ClassBinaryCache.kt)) Stores transformed bytecode to avoid re-running expensive ASM transformations. It keys entries by FQCN + `transformationId`, ensuring that if the transform logic changes, the cache is invalidated.
-   **Transformation Failures**: Detailed logs if a transformation fails due to malformed bytecode or unsupported patterns.

## Testing Class Loading

-   **Transformation Tests**: Verify that ASM-based transformations correctly modify bytecode without breaking class structure.
-   **Leak Detection**: Tests confirm that discarding a `ModuleClassLoader` doesn't lead to `Metaspace` or heap memory leaks.
