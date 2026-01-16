# Preview Designer Architecture (Common)

The Preview Designer framework provides common infrastructure for various live preview and interactive editing environments in Android Studio (e.g., Compose Preview, Wear OS Tile Preview).

## Core Component Architecture

### 1. CommonPreviewRepresentation
([`CommonPreviewRepresentation.kt`](../src/com/android/tools/idea/preview/representation/CommonPreviewRepresentation.kt))
The base lifecycle manager for preview surfaces.
- **Activation Lifecycle**: Manages `activate()` and `deactivate()` calls from the IDE editor.
- **Invalidation**: Managed by [`PreviewInvalidationManager.kt`](../src/com/android/tools/idea/preview/PreviewInvalidationManager.kt). Tracks changes to source files and marks representations as "out-of-date".

### 2. Preview Refresh Logic
- **`PreviewRefreshManager`**: ([`PreviewRefreshManager.kt`](../src/com/android/tools/idea/preview/PreviewRefreshManager.kt)) Handles the scheduling and prioritization of refresh requests across multiple preview instances.
- **Refresh Strategy**:
    - **Structural Refreshes**: Triggered when the set of previewable elements changes.
    - **Quality Refreshes**: High-fidelity renders performed once user interaction or rapid changes pause.

### 3. Multi-Representation Support
The framework supports displaying multiple different "representations" for the same file (e.g., a "Code" view and a "Preview" view).
- **`PreviewRepresentationManager`**: Manages the selection and switching between these views.

## Threading & Concurrency

Standard dispatchers are used to ensure IDE responsiveness:
- **EDT (UI Thread)**: For all UI updates and user event handling.
- **Background (Worker) Threads**: For file analysis, annotation discovery, and symbol lookup.
- **Render Thread**: Serialized access to Layoutlib and rendering resources.

## Dependencies

- **Preview Designer Framework**: This module itself provides the core components.
- **Designer Module**: Relies on base components like `DesignSurface` for rendering and interaction.
