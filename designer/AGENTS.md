# Designer Agent Guide

This document describes how an AI agent can interact with the Layout Editor (Designer).

## Key Entry Points

### 1. Structural Changes and Undo
Structural changes to the layout (adding/removing/reordering components) MUST be wrapped in an `NlWriteCommandAction`.
- Use `NlWriteCommandActionUtil.run()` to perform atomic updates.
- **Example**:
    ```java
    NlWriteCommandActionUtil.run(component, "Add Widget", () -> {
        model.addComponent(newComponent, parent, null);
    });
    ```

### 2. Scene vs Model
- **NlModel**: Represents the "Source of Truth" (the XML). Use this for data-driven changes (attributes, hierarchy).
- **Scene**: Represents the "Interaction Layer". Use `SceneManager` to calculate layout positions or handle constraints without touching the XML immediately.
- **`NlComponentBackend`**: ([`NlComponentBackend.kt`](src/com/android/tools/idea/common/model/NlComponentBackend.kt)) Agents should use the backend to perform low-level XML operations. `NlComponent.tag` provides the underlying `XmlTag`.

### 3. View Modification
- **Attributes**: Use `NlComponent.setAttribute()` for simple changes. This maintains the internal cache.
- **Transactions**: For multiple attribute changes, use `AttributesTransaction` ([`AttributesTransaction.java`](src/com/android/tools/idea/common/model/AttributesTransaction.java)) to batch PSI events and improve performance.
- **Clean-up**: Always call `backend.reformatAndRearrange()` after significant XML edits to maintain file style and consistent indentation.

## Interaction Handling
- **`InteractionHandler`**: ([`InteractionHandler.java`](src/com/android/tools/idea/uibuilder/surface/InteractionHandler.java)) The entry point for mouse/keyboard events. Agents can simulate user input by invoking `createInteractionOn()` for specific ViewHandlers.

## Important Considerations for Agents
- **Dumb Mode**: Many operations (like finding a View's handler based on its FQN) require indices. Check `DumbService.isDumb(project)` before performing actions that depend on symbol resolution.
- **Asynchronous Rendering**: Previews are rendered in-process using Layoutlib. Use `RenderService.getLastRenderResult()` to check if the current view is up-to-date. If `renderResult.isValid()` returns false, the image may be stale.

## Examples and Testing
- **Integration Tests**: `com.android.tools.idea.uibuilder.surface.NlDesignSurfaceTest` shows how to mock user interactions (clicks, drags) on the surface.
- **Constraint Layout**: `com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandlerTest` demonstrates how to test complex auto-connect logic.

### How to run tests
Use Bazel for the core designer tests:
```bash
bazel test //tools/adt/idea/designer:intellij.android.designer_tests
```

## Related Documentation
- [Layout Editor Architecture](docs/architecture.md)
