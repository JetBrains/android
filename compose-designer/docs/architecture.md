# Compose Preview Architecture

The Compose Preview logic provides a live preview and interactive editing environment for Jetpack Compose. It extends the core [Preview Designer Architecture](../../preview-designer/docs/architecture.md).

## Core Component Interaction & Lifecycle

### 1. ComposePreviewRepresentation
([`ComposePreviewRepresentation.kt`](../src/com/android/tools/idea/compose/preview/ComposePreviewRepresentation.kt))
Specific implementation of `CommonPreviewRepresentation` for Compose.
- **Dumb Mode Handling**: Discovery is paused during indexing to avoid incomplete or inaccurate results from the project's symbol table.

### 2. Preview Discovery & Rendering
- **Discovery**: `AnnotationFilePreviewElementFinder` uses the Kotlin analysis API to scan for `@Preview` annotations.
- **`ComposePreviewElement`**: ([`ComposePreviewElement.kt`](../../preview-elements/src/com/android/tools/preview/ComposePreviewElement.kt)) Each discovery result is encapsulated in this object, which contains the FQN of the Composable and its configuration parameters.
- **Compose Renderer & Synthetic XML**: ([`ComposeRenderer.kt`](../src/com/android/tools/idea/compose/preview/renderer/ComposeRenderer.kt))
    - **Bridge**: The system does NOT render Compose directly. It generates a synthetic XML layout (via `ComposePreviewElementModelAdapter.toXml()`) that contains a customized `ComposeViewAdapter`.
    - **LightVirtualFile**: This XML is stored in memory as a `ComposeAdapterLightVirtualFile`, which `Layoutlib` parses.
    - **Runtime**: When Layoutlib inflates this XML, the adapter initializes the Compose runtime and executes the `@Composable` function identified by the `tools:composableName` attribute.

### 3. Fast Preview & Live Editing
([`FastPreviewManager.kt`](../../android/src/com/android/tools/idea/editors/fast/FastPreviewManager.kt))
To bypass full Gradle builds, the system uses:
- **On-the-fly Compilation**: Uses the Kotlin compiler (via the Analysis API) to compile only the modified Composable functions into memory.
- **ClassLoader Hot-Swapping**: The `ModuleClassLoader` loads these "hot" classes, allowing the renderer to see code changes in sub-second time. See [Class Loading Architecture](../../rendering/docs/classloading.md).

## Threading & Concurrency

- **EDT (UI Thread)**: UI updates, selection tracking, and navigation. Use `Dispatchers.Main` for this.
- **Worker Thread**: Background discovery and symbol analysis. Use the `Dispatchers.Default` dispatcher for this.
- **Render Thread**: All calls to Layoutlib and the Compose runtime are serialized on this thread to prevent race conditions.

## Interaction Layer
Interactive mode allows users to "run" the Composable within the designer.
- **Event Dispatch**: Raw input events are captured by the `DesignSurface` and forwarded to the local Compose `PointerInput` system.
- **State Preservation**: The interactive session maintains `remember` and `mutableStateOf` variables across frames, ensuring that animations and user interactions (like clicking a counter) work as expected.

## Testing Architecture

- **`ComposePreviewRepresentationTest`**: Verifies state transitions (Active -> Inactive -> Disposed).
- **`AnnotationFilePreviewElementFinderTest`**: Ensures complex `@Preview` parameter combinations are correctly parsed.
- **Golden Image Comparison**: Used to detect pixel regressions in rendering output.

## Dependencies
- **Kotlin Analysis API**: Used for finding and analyzing `@Composable` functions.
- **Layoutlib**: JVM backend for rendering.
- **Preview Designer Framework**: This module extends the common [Preview Designer Architecture](../../preview-designer/docs/architecture.md).
