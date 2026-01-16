# Layout Editor Architecture

The Layout Editor (Designer) is a highly extensible framework for interactive UI preview and building. Its core abstractions are also leveraged by specialized designers like **Compose Preview** and **Wear OS Designer**.

> [!NOTE]
> While originally XML-centric, modern non-XML modules like `preview-designer` and `compose-designer` rely heavily on the components and surface abstractions defined here.

## Core Component Interaction & State Management

### 1. NlDesignSurface and Layering
([`NlDesignSurface.kt`](../src/com/android/tools/idea/uibuilder/surface/NlDesignSurface.kt))
The central UI container. It uses a sophisticated layering system to separate concerns:
- **`ScreenView`**: The base layer that draws the `BufferedImage` rendered by Layoutlib.
- **`Scene` Layer**: ([`Scene.java`](../../preview-designer/src/com/android/tools/idea/common/scene/Scene.java)) The vector-based interaction layer. It handles selection, hover states, and drawing of interaction handles (e.g., resize, constraints).
- **`GlassLayer`**: The topmost transparent pane that intercepts AWT mouse events and dispatches them to the `InteractionHandler`.
- **Split Editor Support**: ([`SplitEditor.kt`](../../common/src/com/android/tools/idea/common/editor/SplitEditor.kt)) The Designer integrates with IntelliJ's split-view system to provide side-by-side "Code" and "Design" views.
- **`InteractionHandler`**: ([`InteractionHandler.java`](../src/com/android/tools/idea/uibuilder/surface/InteractionHandler.java)) The bridge between raw inputs and semantic actions. It delegates to:
    - **`ViewHandler`**: For component-specific interactions (e.g., resizing a Button).
    - **`ScaleController`**: For zoom gestures.
    - **`PanController`**: For panning the viewport.
    - **`MarqueeSelection`**: For rectangular selection on the background.

### 2. NlModel & PSI Synchronization
([`NlModel.kt`](../src/com/android/tools/idea/common/model/NlModel.kt))
The `NlModel` is the data backbone, mirroring the XML's **PSI (Program Structure Interface)**.
- **`NlComponent`**: ([`NlComponent.java`](../src/com/android/tools/idea/common/model/NlComponent.java)) A light-weight wrapper around a `XmlTag`. It caches attributes and maintains a parent-child relationship that matches the XML hierarchy.
- **Model Reconciliation**: When the XML file is edited (directly or via a refactoring), the `DefaultModelUpdater` performs a tree diff between the new PSI and the existing `NlComponent` tree. It updates only the modified branches to preserve state like component selection.
- **Transactional Writes**: Any change from the UI (e.g., dragging a widget) is wrapped in an `NlWriteCommandAction`. This ensures that multiple PSI changes (tag addition, attribute setting) are committed as a single undoable block.

### 3. Multi-Representation System
([`MultiRepresentationPreview.kt`](../src/com/android/tools/idea/uibuilder/editor/multirepresentation/MultiRepresentationPreview.kt))
For modern UI frameworks (like Compose or Wear OS Tiles), the Designer provides a multi-representation system.
- **`PreviewRepresentation`**: A modular unit of preview logic that can be swapped or combined.
- **`PreviewRepresentationManager`**: Manages the lifecycle and discovery of these units for a specific file type.

### 4. Scene vs. Model Separation
The framework separates **Data (Model)** from **Interaction (Scene)**:
- **NlModel**: Pure tree of components; oblivious to pixel positions.
- **Scene**: Geometric representation. Each `SceneComponent` has `x, y, w, h` coordinates.
- **`LayoutlibSceneManager`**: The bridge that receives a `RenderResult` from Layoutlib (containing `ViewInfo` hierarchy) and synchronization the `SceneComponent` bounds with the actual rendered pixels.

## Threading Model

- **EDT (Event Dispatch Thread)**: Processes all UI events, selection logic, and property panel updates.
- **Render Thread**: Managed via `RenderAsyncActionExecutor`. Heavily latent tasks (like full layout inflation) are offloaded to this thread to keep the IDE responsive.

## Extensibility via ViewHandlers
([`ViewHandler.kt`](../src/com/android/tools/idea/uibuilder/api/ViewHandler.kt))
Every Android view type can have a dedicated `ViewHandler`:
- **Layout Logic**: Handlers for `ConstraintLayout` or `LinearLayout` define how children are snapped, moved, or resized.
- **Component Palette**: Handlers provide the metadata (icon, default attributes) used in the widget palette.
- **Specialized Interactivity**: For example, the `ConstraintLayoutHandler` provides complex "Auto-Connect" and "Inference" logic.

## Testing Architecture

- **`NlDesignSurfaceTest`**: Mocks user gestures (clicks, drags) and verifies both the resulting XML and visual state.
- **`NlModelTest`**: Verifies the reconciliation logic when the underlying XML is modified.
- **`InteractionHandlerTest`**: Verifies that raw mouse events are correctly translated into high-level interactions like "Marquee Select" or "Drag and Drop".

## Dependencies
- **Layoutlib**: JVM implementation of the Android framework for rendering.
- **PSI/XML**: IntelliJ's data structures for file representation.
- **Render System**: Shared infrastructure for managing rendering tasks.
