# Rendering Architecture (UI Tools)

The Rendering component is the backend for all visual design tools in Android Studio, bridging the IDE and a JVM-hosted implementation of the Android UI framework (**Layoutlib**).

## Core Component Interface & Lifecycle

### 1. RenderService and Task Management
([`RenderService.java`](../src/com/android/tools/rendering/RenderService.java))
The entry point for rendering. It manages a persistent pool of `RenderTask`s.
- **RenderTask**: ([`RenderTask.java`](../src/com/android/tools/rendering/RenderTask.java)) A stateful object representing a render session. It holds the `Configuration` (Device, Theme, Locale) and the `ModuleClassLoader`.
- **Async Execution**: Tasks are handled by the [`RenderAsyncActionExecutor.kt`](../src/com/android/tools/rendering/RenderAsyncActionExecutor.kt). The `render()` call returns a `CompletableFuture<RenderResult>`, allowing the caller to react to render completion/failure without blocking the UI thread.

### 2. Layoutlib Lifecycle
- **Initialization**: `LayoutlibLoader` loads the `layoutlib.jar` bundled with Android Studio (and its corresponding framework data files).
- **Session Bridge**: A `RenderSession` is created within Layoutlib to maintain the state of the inflated UI. This session allows for incremental updates (e.g., changing a single view's visibility) without a full re-inflation.

### 3. Class Loading and Transformations
([`ModuleClassLoader.kt`](../src/com/android/tools/rendering/classloading/ModuleClassLoader.kt))
Crucial for running Android code on a standard JVM. For a deep dive into how classes are isolated and transformed, see the [Class Loading & ModuleClassLoader Documentation](classloading.md).
- **ASM Transformations**: The classloader uses ASM to transform bytecode on-the-fly.
- **Dependency Isolation**: It ensures that dependencies from the project don't leak into the IDE's own classloader.

## High-Level Data Flow

1. **Task Creation**: A client calls `RenderService.taskBuilder()` with a module and configuration.
2. **Inflation**: `RenderTask.inflate()` builds the view hierarchy within Layoutlib.
3. **Rendering**: `render()` draws the hierarchy to an off-screen `BufferedImage`.
4. **ViewInfo Extraction**: Layoutlib provides a tree of `ViewInfo` objects. These contain the exact coordinates of every view, which are used by the Designer for interaction mapping and by **Layout Inspector** for hierarchy reconstruction.

## Threading Model & Security

### Rendering Serialization
- **Serialization**: All Layoutlib calls are serialized on a single "render thread" via [`RenderExecutor.kt`](../src/com/android/tools/rendering/RenderExecutor.kt). This is mandatory because Layoutlib is not thread-safe and relies on global state (like the Resource Repository).

### Render Security Manager
([`RenderSecurityManager.java`](../src/com/android/tools/rendering/security/RenderSecurityManager.java))
Since rendering involves executing arbitrary user code (Custom Views), the system employs a strict security manager:
- **Sandboxing**: Restricts file system access, network access, and system property modifications.
- **Thread Control**: Monitored by the security manager to prevent background threads spawned by user code from escaping the render context.

## Error Handling & Logging
([`RenderLogger.java`](../src/com/android/tools/rendering/RenderLogger.java))
- **Capture**: The logger captures all exceptions and layout errors (e.g., missing resources, inflation failures) that occur during the render lifecycle.
- **Reporting**: These are surfaced to the user via the IDE's **Problems** (or **Issues**) panel.

## Testing Architecture

- **Golden Image Comparison**: The primary validation method. `RenderTest` runs a render and compares the output pixel-by-pixel against a "golden" reference image.
- **ClassLoader Tests**: Verifies that specific transformations (like those required for Compose) are correctly applied.
- **Resource Leak Tests**: Ensures that `RenderTask.dispose()` correctly releases Layoutlib sessions and native resources.

## Dependencies
- **Layoutlib**: JVM implementation of the Android Framework.
- **Resource Repository**: Source of truth for XML and asset data.
- **Kotlin Analysis API**: Used by the Compose-specific rendering bridge.

## Standalone Rendering Stack (Screenshot Testing)
([`tools/base/standalone-render`](../../../../base/standalone-render))

The rendering infrastructure is designed to run outside of Android Studio for headless use cases like screenshot testing (`previewscreenshot` plugin). This "Standalone" mode replaces IDE-specific components with lightweight alternatives:

| Android Studio Component | Standalone Replacement | Key Differences |
| :--- | :--- | :--- |
| **`RenderModelModule`** | **`StandaloneRenderModelModule`** | No `NlModel`/`PsiFile`. Uses raw file paths and in-memory resource repositories. |
| **`ModuleClassLoader`** | **`DefaultModuleClassLoader`** | Delegates to `UrlClassLoader`. No PSI-based cache invalidation. Single-threaded preloading. |
| **`FrameworkResourceRepositoryManager`** | **`Standalone...Provider`** | Does NOT cache framework resources to disk (no `.idea/caches`). Uses simple in-memory maps. |
| **`AssetRepository`** | **`AssetRepositoryBase`** | Direct file system access via `FileInputStream`. No VirtualFile system overlay. |

This separation allows the rendering engine (Layoutlib bridge, View inflation) to be reused in CI environments without the overhead of booting the full IntelliJ platform.
