# Layout Inspector Architecture

The Layout Inspector provides real-time visibility into the UI hierarchy, properties, and state of a running Android application, supporting both View-based and Compose-based UIs.

## Data Pipeline & Communication

### 1. InspectorClient and the App Inspection Bridge
([`InspectorClient.kt`](../src/com/android/tools/idea/layoutinspector/pipeline/InspectorClient.kt))
The bridge between the IDE and the device.
- **Protocol**: Communication happens via the **App Inspection** framework, leveraging gRPC over the **Android Transport** layer. This provides a reliable, multiplexed stream for both commands (e.g., "capture snapshot") and data (e.g., hierarchy updates).
- **Agent Injection**: Upon session start, the IDE injects `inspector-agent.dex` (and its native companion) into the target process. This agent hooks into the `Choreographer` to capture frame data without significant overhead.
- **Dynamic Capabilities**: The client performs a handshake to query the agent for supported features (`CAN_GET_PROPERTIES`, `CAN_RESOLVE_LOCALS`), allowing it to adapt to different SDK versions and app configurations.
- **Connection Management**: ([`InspectorClientLauncher.kt`](../src/com/android/tools/idea/layoutinspector/pipeline/InspectorClientLauncher.kt)) Manages the complex state machine of attaching to a process, including:
    - **Discovery**: Detecting when a debuggable process starts.
    - **Port Forwarding**: Setting up ADB forwarding for legacy clients.
    - **Retry Logic**: Handling sporadic connection failures during app startup.

### 2. InspectorModel and Snapshot Processing
([`InspectorModel.kt`](../src/com/android/tools/idea/layoutinspector/model/InspectorModel.kt))
- **Hierarchy Reconstruction**: The `LayoutInspectorProcessor` consumes raw protobuf packets.
- **`TreeLoader`**: ([`TreeLoader.kt`](../src/com/android/tools/idea/layoutinspector/pipeline/TreeLoader.kt)) Responsible for parsing the raw structure and merging it with existing nodes to preserve selection and expansion state across updates.
- **Compose Integration**: Compose components are mapped to `ComposeViewNode`s. These include semantic data and source code locations (File, Line) for easy "Jump to Source".
- **SKP (Skia Picture) Processing**: For high-fidelity 3D mode, the agent sends SKP data. The IDE uses its own bundled Skia engine to de-serialize and render these snapshots, allowing for precise coordinate mapping even at extreme rotation angles.
- **Recomposition Tracking**: The agent instruments the Compose runtime's `Composer` instances. It streams recomposition and skip counts, which the `InspectorModel` aggregates per-node to help developers find performance bottlenecks.

## Threading Model

- **Transport Thread**: Handles raw gRPC stream reception and initial packet decoding from the `StudioTransport` service.
- **Processor Thread**: A dedicated background thread for building the `InspectorModel`. This involves heavy geometry calculations and Skia de-serialization.
- **EDT (Event Dispatch Thread)**: Updates the Tree and 3D Surface whenever the model fires a change event.

## Testing Architecture

- **`TestLayoutInspectorModelBuilder.kt`**: A DSL that allows developers to defined complex UI hierarchies (including nested Compose nodes) to test filtering and property resolution logic.
- **`AppInspectionInspectorClientTest`**: Verifies the handshake and data streaming logic with a mocked version of the App Inspection server.
- **Recomposition Tests**: Ensures counts are correctly aggregated and displayed in the property panel.

## Dependencies
- **Transport**: Underlying gRPC communication layer.
- **App Inspection Framework**: Modern device-side instrumentation.
- **Skia**: Used for 2D/3D layout representation.
