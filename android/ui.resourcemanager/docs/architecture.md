# Resource Manager Architecture

The Resource Manager is the centralized hub for managing Android assets, providing high-fidelity previews and seamless integration with the Layout Editor and Compose.

## Component Architecture & Notification System

### 1. ResourceManager Service
([`ResourceManager.java`](../../../../android/src/com/android/tools/idea/ui/resourcemanager/ResourceManager.java))
The orchestrator for the tool window. It manages the lifecycle of the explorer tabs (Drawables, Colors, Layouts) and delegates data fetching to specialized providers.

### 2. ResourceNotificationManager and Live Updates
([`ResourceNotificationManager.java`](../../../../adt/idea/android-common/src/com/android/tools/idea/res/ResourceNotificationManager.java))
The Resource Manager relies on a robust notification system to stay in sync with the file system:
- **`ResourceRepository` Integration**: It listens to the `ResourceRepository` (part of `project-system`), which provides an indexed view of all XML and asset files.
- **FS Listeners**: It attaches listeners to the project's resource directories.
- **Batching**: Changes are batched and debounced to prevent UI flickering during large operations like asset imports or branch switches.
- **Cache Invalidation**: When a file changes, the `ResourceDataProvider` invalidates only the affected resource's metadata and preview.

### 3. Resource Importer and Asset Transformation
([`ResourceImporter.kt`](../../../../android/src/com/android/tools/idea/ui/resourcemanager/importer/ResourceImporter.kt))
- **Multi-step Import**: Importing an asset (like an SVG) involves:
    1. **Validation**: Checking for supported features, namespaces, and potential name collisions.
    2. **Conversion**: Invoking the **Vector Drawable Converter** ([`VdConverter.java`](../../../../../../base/common/src/main/java/com/android/ide/common/vectordrawable/VdConverter.java)) to transform source formats into Android-compliant XML.
    3. **PSI Insertion**: Structurally adding the new file to the project using `WriteCommandAction`, ensuring correct directory placement (e.g., `drawable-anydpi-v24`).

## Data Flow & Preview Generation

1. **Hierarchy Discovery**: `ResourceRepository` provides the raw list of resource items across all modules and library dependencies.
2. **Metadata Enrichment**: `ResourceDataProvider` extracts version qualifiers, densities, and configurations.
3. **Async Previewing**:
    - **`AssetDataProvider`**: A core interface for resolving the actual visual asset (Bitmap, Drawable) from the resource item.
    - **`SlowResourcePreviewManager`**: Handles heavy preview generation (like large Drawables or complex Layouts) on a background thread to prevent UI freeze.
    - **Caching**: Generated previews are cached in memory (and potentially on disk) to allow instant scrolling performance in the explorer grid.

## Testing Architecture

- **`ResourceManagerTest`**: Ensures that resources across different modules are correctly indexed and visible.
- **`ResourceImporterTest`**: Verifies that complex SVG features (gradients, paths) are correctly converted to Vector Drawables.
- **UI Integration Tests**: Mocks drag-and-drop operations from the manager into an `NlDesignSurface` to verify PSI modification accuracy.

## Dependencies
- **Resource Repository**: Source of truth for all indexed resources.
- **Layoutlib**: Used for high-fidelity previews of XML assets.
- **DesignSurface**: Target for drag-and-drop asset insertion.
