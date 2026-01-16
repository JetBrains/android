# Layout Inspector Agent Guide

This document provides a high-level map for AI agents interacting with the Layout Inspector. For common architectural details, see the [Layout Inspector Architecture](docs/architecture.md).

## Core Package Structure

- **`com.android.tools.idea.layoutinspector.model`**: Contains the `InspectorModel` and node types (`ViewNode`, `ComposeViewNode`). This is the primary source of truth for the UI hierarchy.
- **`com.android.tools.idea.layoutinspector.pipeline`**: Manages the connection to the device and the data stream processing. Key classes: `InspectorClient`, `TreeLoader`.
- **`com.android.tools.idea.layoutinspector.properties`**: Handles the fetching and presentation of View/Compose properties.
- **`com.android.tools.idea.layoutinspector.ui`**: Contains the 2D/3D renderers and the component tree UI.

## Key Agent Interaction Points

- **Model Inspection**: Agents should start by querying the `InspectorModel` to understand the current state of the application's UI.
- **Compose Support**: Recomposition counts and source code resolution are available for Compose nodes.
- **Connectivity**: Check `InspectorClient.isConnected` before attempting to perform actions that require a live device link.

## Examples and Testing

### Testing Utilities
- **`TestLayoutInspectorModelBuilder.kt`**: Use this DSL (in `testingSrc`) to create mock UI hierarchies for unit tests.
- **App Inspection Mocks**: `AppInspectionInspectorClientTest` provides a reference for mocking the device-side data stream.

### How to run tests
Use Bazel for the layout inspector tests:
```bash
bazel test //tools/adt/idea/layout-inspector:intellij.android.layout-inspector_tests
```
